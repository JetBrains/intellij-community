// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.fileTypes.impl

import com.intellij.lang.xml.XMLLanguage

class SvgLanguage private constructor() : XMLLanguage(XMLLanguage.INSTANCE, "SVG", "image/svg+xml") {
  companion object {
    @JvmField
    val INSTANCE: SvgLanguage = SvgLanguage()
  }
}