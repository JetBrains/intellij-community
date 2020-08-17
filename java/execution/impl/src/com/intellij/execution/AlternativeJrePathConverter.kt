// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution

import com.intellij.execution.ui.BundledJreProvider
import com.intellij.util.JdkBundle
import com.intellij.util.xmlb.Converter

/**
 * @author Konstantin Bulenkov
 */
class AlternativeJrePathConverter: Converter<String>() {
  private val BUNDLED = "BUNDLED"
  private val jbr = JdkBundle.createBundled()
  override fun fromString(value: String): String? {
    if (value == BUNDLED) {
      val path = jbr?.location?.path
      if (path != null) return BundledJreProvider.getPatchedJrePath(path)
    }
    return value
  }

  override fun toString(value: String): String? {
    val path = jbr?.location?.path
    if (path != null && value == BundledJreProvider.getPatchedJrePath(path)) {
      return BUNDLED
    }
    return value
  }
}