// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.utils

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage

class ImageTransformationData(
  private val originalImage: BufferedImage,
  private var rotationAngle: Int = 0,
  private var isNormalized: Boolean = false
) {
  companion object {
    private val IMAGE_TRANSFORMATION_DATA_KEY: Key<ImageTransformationData> = Key("IMAGE_TRANSFORMATION_DATA")
    val ORIGINAL_IMAGE_KEY: Key<BufferedImage> = Key("ORIGINAL_IMAGE")
    val CURRENT_NOT_NORMALIZED_IMAGE_KEY: Key<BufferedImage> = Key("CURRENT_NOT_NORMALIZED_IMAGE")
    val CURRENT_ANGLE_KEY: Key<Int> = Key("CURRENT_ANGLE")

    fun getInstance(imageFile: VirtualFile?): ImageTransformationData? {
      val originalImage = imageFile?.getUserData(ORIGINAL_IMAGE_KEY) ?: return null
      return imageFile.getUserData(IMAGE_TRANSFORMATION_DATA_KEY) ?: ImageTransformationData(originalImage).also {
        imageFile.putUserData(IMAGE_TRANSFORMATION_DATA_KEY, it)
      }
    }
  }

  suspend fun applyTransformations(image: BufferedImage): BufferedImage = withContext(Dispatchers.IO) {
    var result = image
    if (isNormalized) {
      result = ScientificUtils.normalizeImage(result)
    }
    if (rotationAngle != 0) {
      result = ScientificUtils.rotateImage(result, rotationAngle)
    }
    result
  }

  fun setRotationAngle(angle: Int) {
    rotationAngle = angle
  }

  fun setIsNormalized(normalizationStatus: Boolean) {
    isNormalized = normalizationStatus
  }

  fun getRotationAngle(): Int = rotationAngle

  fun isNormalized(): Boolean = isNormalized
}