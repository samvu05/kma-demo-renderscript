package com.sam.demorenderscrip


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.renderscript.*
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.sam.demorenderscrip.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var mBitmapIn: Bitmap
    private lateinit var mBitmapOut: Bitmap

    private lateinit var mRS: RenderScript
    private lateinit var mInAllocation: Allocation
    private lateinit var mOutAllocation: Allocation

    private lateinit var mScriptBlur: ScriptIntrinsicBlur
    private lateinit var mScriptConvolve: ScriptIntrinsicConvolve5x5
    private lateinit var mScriptMatrix: ScriptIntrinsicColorMatrix

    private val MODE_BLUR = 0
    private val MODE_CONVOLVE = 1
    private val MODE_COLORMATRIX = 2

    private var mFilterMode = MODE_BLUR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        resetBitmap()
        buildScipt()

        binding.navBottom.setOnNavigationItemSelectedListener {
            binding.seekbar.progress = 0
            resetBitmap()
            when (it.itemId) {
                R.id.navbot_item_blur -> {
                    mFilterMode = MODE_BLUR
                }
                R.id.navbot_item_details -> {
                    mFilterMode = MODE_CONVOLVE
                }
                R.id.navbot_item_color -> {
                    mFilterMode = MODE_COLORMATRIX
                }
            }
            true
        }

        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progess: Int, p2: Boolean) {
                when (mFilterMode) {
                    MODE_BLUR -> blurBitmap(getFilterParameter(progess))
                    MODE_CONVOLVE -> convolveBitmap(getFilterParameter(progess))
                    MODE_COLORMATRIX -> matrixBitmap(getFilterParameter(progess))
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })
    }

    private fun buildScipt() {
        mRS = RenderScript.create(this)
        mInAllocation = Allocation.createFromBitmap(mRS, mBitmapIn)
        mOutAllocation = Allocation.createFromBitmap(mRS, mBitmapOut)
        mScriptBlur = ScriptIntrinsicBlur.create(
            mRS,
            Element.U8_4(mRS)
        )
        mScriptConvolve = ScriptIntrinsicConvolve5x5.create(
            mRS,
            Element.U8_4(mRS)
        )
        mScriptMatrix = ScriptIntrinsicColorMatrix.create(
            mRS,
            Element.U8_4(mRS)
        )
    }

    private fun resetBitmap() {
        mBitmapIn = loadBitmap(R.drawable.iv_bg_demo)
        mBitmapOut = Bitmap.createBitmap(mBitmapIn.width, mBitmapIn.height, Bitmap.Config.ARGB_8888)
        binding.ivHome.setImageResource(R.drawable.iv_bg_demo)
    }

    private fun loadBitmap(resource: Int): Bitmap {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        return BitmapFactory.decodeResource(resources, resource, options)
    }

    fun setBackgroundImageView(bitmap: Bitmap) {
        binding.ivHome.setImageBitmap(bitmap)
    }

    private fun blurBitmap(radius: Float) {
        mScriptBlur.setRadius(radius)

        //Perform the Renderscript
        mScriptBlur.setInput(mInAllocation)
        mScriptBlur.forEach(mOutAllocation)

        //Copy the final bitmap created by the out Allocation to the outBitmap
        mOutAllocation.copyTo(mBitmapOut)

        setBackgroundImageView(mBitmapOut)

        //Recycle the original bitmap
        mBitmapIn.recycle()
        mRS.destroy()
    }

    private fun convolveBitmap(radius: Float) {
        val f1: Float = radius
        val f2 = 1.0f - f1

        // Emboss filter kernel
        val coefficients = floatArrayOf(
            -f1 * 2, 0f, -f1, 0f, 0f, 0f, -f2 * 2, -f2, 0f, 0f,
            -f1, -f2, 1f, f2, f1, 0f, 0f, f2, f2 * 2, 0f, 0f, 0f, f1, 0f, f1 * 2
        )
        // Set kernel parameter
        mScriptConvolve.setCoefficients(coefficients)

        // Invoke filter kernel
        mScriptConvolve.setInput(mInAllocation)
        mScriptConvolve.forEach(mOutAllocation)

        mOutAllocation.copyTo(mBitmapOut)

        setBackgroundImageView(mBitmapOut)

        //Recycle the original bitmap
        mBitmapIn.recycle()
        mRS.destroy()
    }

    private fun matrixBitmap(radius: Float) {
        val cos = Math.cos(radius.toDouble()).toFloat()
        val sin = Math.sin(radius.toDouble()).toFloat()
        val mat = Matrix3f()
        mat[0, 0] = (.299 + .701 * cos + .168 * sin).toFloat()
        mat[1, 0] = (.587 - .587 * cos + .330 * sin).toFloat()
        mat[2, 0] = (.114 - .114 * cos - .497 * sin).toFloat()
        mat[0, 1] = (.299 - .299 * cos - .328 * sin).toFloat()
        mat[1, 1] = (.587 + .413 * cos + .035 * sin).toFloat()
        mat[2, 1] = (.114 - .114 * cos + .292 * sin).toFloat()
        mat[0, 2] = (.299 - .3 * cos + 1.25 * sin).toFloat()
        mat[1, 2] = (.587 - .588 * cos - 1.05 * sin).toFloat()
        mat[2, 2] = (.114 + .886 * cos - .203 * sin).toFloat()
        mScriptMatrix.setColorMatrix(mat)
        mScriptMatrix.forEach(mInAllocation, mOutAllocation)
        mOutAllocation.copyTo(mBitmapOut)

        setBackgroundImageView(mBitmapOut)

        //Recycle the original bitmap
        mBitmapIn.recycle()
        mRS.destroy()
    }

    private fun getFilterParameter(i: Int): Float {
        var f = 0f
        when (mFilterMode) {
            MODE_BLUR -> {
                val max = 25.0f
                val min = 1f
                f = ((max - min) * (i / 100.0) + min).toFloat()
            }
            MODE_CONVOLVE -> {
                val max = 2f
                val min = 0f
                f = ((max - min) * (i / 100.0) + min).toFloat()
            }
            MODE_COLORMATRIX -> {
                val max = Math.PI.toFloat()
                val min = (-Math.PI).toFloat()
                f = ((max - min) * (i / 100.0) + min).toFloat()
            }
        }
        return f
    }
}

