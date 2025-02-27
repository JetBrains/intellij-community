// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific

import com.intellij.openapi.util.Key
import java.awt.image.BufferedImage

object ScientificUtils {
  val SCIENTIFIC_MODE_KEY: Key<Unit> = Key<Unit>("SCIENTIFIC_MODE")
  val ORIGINAL_IMAGE_KEY: Key<BufferedImage> = Key("ORIGINAL_IMAGE")
  const val DEFAULT_IMAGE_FORMAT: String = "png"
}
