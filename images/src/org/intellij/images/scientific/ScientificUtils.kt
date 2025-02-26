// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific

import com.intellij.openapi.util.Key
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object ScientificUtils {
  val SCIENTIFIC_MODE_KEY: Key<Unit> = Key<Unit>("SCIENTIFIC_MODE")
  val ORIGINAL_IMAGE_KEY: Key<BufferedImage> = Key("ORIGINAL_IMAGE")
}

internal fun convertToByteArray(image: BufferedImage, imageType: String): ByteArray {
  val outputStream = ByteArrayOutputStream()
  ImageIO.write(image, imageType, outputStream)
  return outputStream.toByteArray()
}