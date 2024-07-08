// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import org.jdom.Element

object  CustomCodeStyleSettingsUtils {
  private const val VERSION_ATTR_NAME = "version"

  @JvmStatic
  fun readVersion(element: Element?): Int = element?.getAttributeValue(VERSION_ATTR_NAME)?.trim()?.let {
    try {
      it.toInt()
    }
    catch (e: NumberFormatException) {
      null
    }
  } ?: 0

  @JvmStatic
  fun writeVersion(element: Element?, version: Int) = element?.setAttribute(VERSION_ATTR_NAME, version.toString())
}