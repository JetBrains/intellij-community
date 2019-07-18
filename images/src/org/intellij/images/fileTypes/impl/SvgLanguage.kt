// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.fileTypes.impl

import com.intellij.lang.xml.XMLLanguage
import com.intellij.lang.xml.XMLParserDefinition
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.xml.XmlFileImpl
import com.intellij.psi.tree.IFileElementType

class SvgLanguage : XMLLanguage(XMLLanguage.INSTANCE, "SVG", "image/svg+xml") {
  companion object {
    @JvmField val INSTANCE = SvgLanguage()
  }
}

class SvgParserDefinition : XMLParserDefinition() {
  override fun getFileNodeType(): IFileElementType = SVG_FILE

  override fun createFile(viewProvider: FileViewProvider): PsiFile = XmlFileImpl(viewProvider, SVG_FILE)

  companion object {
    val SVG_FILE = IFileElementType(SvgLanguage.INSTANCE)
  }
}
