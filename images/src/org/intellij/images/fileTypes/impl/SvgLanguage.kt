// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.fileTypes.impl

import com.intellij.lang.xml.XMLLanguage

class SvgLanguage private constructor(): XMLLanguage(XMLLanguage.INSTANCE, "SVG", "image/svg+xml") {
  companion object {
    @JvmField
    val INSTANCE = SvgLanguage()
  }
}