// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.fileTypes.impl

import com.intellij.lang.xml.XMLParserDefinition
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.xml.XmlFileImpl
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.annotations.NotNull

internal class SvgParserDefinition : XMLParserDefinition() {

  override fun getFileNodeType(): IFileElementType = SVG_FILE

  override fun createFile(viewProvider: @NotNull FileViewProvider): @NotNull PsiFile = XmlFileImpl(viewProvider, SVG_FILE)

  private val SVG_FILE = IFileElementType(SvgLanguage.INSTANCE)
}