// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import org.jdom.Element

/**
 * This class should be used to read/write settings during migration process of [CustomCodeStyleSettings]
 */
object  CustomCodeStyleSettingsUtils {
  private const val VERSION_ATTR_NAME = "version"

  /**
   * Reads version of the current [CustomCodeStyleSettings]
   * @param element XmlElement, which stores settings for the certain [CodeStyleScheme]
   * @return version if it was parsed successfully, 0 otherwise
   */
  @JvmStatic
  fun readVersion(element: Element?): Int = element?.getAttributeValue(VERSION_ATTR_NAME)?.trim()?.let {
    try {
      it.toInt()
    }
    catch (e: NumberFormatException) {
      null
    }
  } ?: 0

  /**
   * Writes version of the current [CustomCodeStyleSettings]
   * @param element XmlElement, which stores settings for the certain [CodeStyleScheme]
   * @param version new version of the settings
   */
  @JvmStatic
  fun writeVersion(element: Element?, version: Int) = element?.setAttribute(VERSION_ATTR_NAME, version.toString())
}