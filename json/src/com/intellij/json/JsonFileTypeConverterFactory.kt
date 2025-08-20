// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.platform.syntax.psi.ElementTypeConverter
import com.intellij.platform.syntax.psi.ElementTypeConverterFactory
import com.intellij.platform.syntax.psi.elementTypeConverterOf

class JsonFileTypeConverterFactory : ElementTypeConverterFactory {

  override fun getElementTypeConverter(): ElementTypeConverter = jsonConverter
  
  private val jsonConverter = elementTypeConverterOf(
    JSON_SYNTAX_FILE to JSON_FILE,
    JSON5_SYNTAX_FILE to JSON5_FILE,
    JSON_LINES_SYNTAX_FILE to JSON_LINES_FILE
  )
}