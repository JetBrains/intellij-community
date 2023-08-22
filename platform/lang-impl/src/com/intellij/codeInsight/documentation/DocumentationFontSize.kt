// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DocumentationFontSize")
@file:Internal

package com.intellij.codeInsight.documentation

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.FontSize
import org.jetbrains.annotations.ApiStatus.Internal

private const val QUICK_DOC_FONT_SIZE_V1_PROPERTY = "quick.doc.font.size" // 2019.3 or earlier versions
private const val QUICK_DOC_FONT_SIZE_V2_PROPERTY = "quick.doc.font.size.v2" // 2020.1 EAP
private const val QUICK_DOC_FONT_SIZE_V3_PROPERTY = "quick.doc.font.size.v3" // 2020.1 or later versions

fun getDocumentationFontSize(): FontSize {
  return fontSizeV3()
         ?: FontSize.SMALL
}

private fun fontSizeV3(): FontSize? {
  return readFontSizeFromSettings(QUICK_DOC_FONT_SIZE_V3_PROPERTY, false)
         ?: fontSizeV2()?.migrateV2ToV3()?.also {
           setDocumentationFontSize(it)
         }
}

private fun fontSizeV2(): FontSize? {
  return readFontSizeFromSettings(QUICK_DOC_FONT_SIZE_V2_PROPERTY, true)
         ?: fontSizeV1()?.migrateV1ToV2()
}

private fun fontSizeV1(): FontSize? {
  return readFontSizeFromSettings(QUICK_DOC_FONT_SIZE_V1_PROPERTY, true)
}

private fun FontSize.migrateV2ToV3(): FontSize {
  return when (this) {
    FontSize.X_SMALL -> FontSize.XX_SMALL
    FontSize.SMALL -> FontSize.X_SMALL
    else -> this
  }
}

private fun FontSize.migrateV1ToV2(): FontSize {
  return when (this) {
    FontSize.X_LARGE -> FontSize.XX_LARGE
    FontSize.LARGE -> FontSize.X_LARGE
    else -> this
  }
}

private fun readFontSizeFromSettings(propertyName: String, unsetAfterReading: Boolean): FontSize? {
  val strValue = PropertiesComponent.getInstance().getValue(propertyName)
                 ?: return null
  if (unsetAfterReading) {
    PropertiesComponent.getInstance().unsetValue(propertyName)
  }
  return try {
    FontSize.valueOf(strValue)
  }
  catch (ignored: IllegalArgumentException) {
    null
  }
}

fun setDocumentationFontSize(x: FontSize) {
  PropertiesComponent.getInstance().setValue(QUICK_DOC_FONT_SIZE_V3_PROPERTY, x.toString())
}
