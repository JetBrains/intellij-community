// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.syntax

import com.intellij.java.syntax.element.JShellSyntaxElementType
import com.intellij.lang.java.JShellParserDefinition
import com.intellij.platform.syntax.psi.ElementTypeConverter
import com.intellij.platform.syntax.psi.ElementTypeConverterFactory
import com.intellij.platform.syntax.psi.elementTypeConverterOf
import com.intellij.psi.impl.source.tree.JShellElementType

internal class JShellElementTypeConverterExtension : ElementTypeConverterFactory {
  override fun getElementTypeConverter(): ElementTypeConverter = jShellConverter

  private val jShellConverter: ElementTypeConverter = elementTypeConverterOf(
    JShellSyntaxElementType.FILE to JShellParserDefinition.FILE_ELEMENT_TYPE,
    JShellSyntaxElementType.ROOT_CLASS to JShellElementType.ROOT_CLASS,
    JShellSyntaxElementType.IMPORT_HOLDER to JShellElementType.IMPORT_HOLDER,
    JShellSyntaxElementType.STATEMENTS_HOLDER to JShellElementType.STATEMENTS_HOLDER,
  )
}
