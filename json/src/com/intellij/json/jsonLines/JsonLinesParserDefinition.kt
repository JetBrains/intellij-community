// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.jsonLines

import com.intellij.json.JsonSyntaxParser
import com.intellij.json.JsonParserDefinition
import com.intellij.json.json5.Json5Language
import com.intellij.json.psi.impl.JsonFileImpl
import com.intellij.json.syntax.JsonSyntaxLexer
import com.intellij.lang.ASTNode
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.psi.ElementTypeConverters
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory.Companion.getInstance
import com.intellij.platform.syntax.psi.impl.getSyntaxParserRuntimeFactory
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType

internal class JsonLinesParserDefinition : JsonParserDefinition() {
  
  companion object {
    @JvmField
    public val JSON_LINES_FILE: SyntaxElementType = SyntaxElementType("JSON_LINES_FILE")
    @JvmField
    public val FILE = object : IFileElementType(JsonLinesLanguage) {
      override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
        val builderFactory = getInstance()
        val elementType = chameleon.getElementType()
        val lexer = JsonSyntaxLexer()
        val syntaxBuilder = builderFactory.createBuilder(chameleon,
                                                         lexer,
                                                         language,
                                                         chameleon.getChars())
        val runtimeParserRuntime =
          getSyntaxParserRuntimeFactory(Json5Language.INSTANCE).buildParserRuntime(syntaxBuilder as SyntaxTreeBuilder)

        JsonSyntaxParser().parse(ElementTypeConverters.getConverter(language).convert(elementType)!!, runtimeParserRuntime)
        return syntaxBuilder.getTreeBuilt().getFirstChildNode()
      }
    }
  }

  override fun createFile(fileViewProvider: FileViewProvider): PsiFile {
    return JsonFileImpl(fileViewProvider, JsonLinesLanguage)
  }

  override fun getFileNodeType(): IFileElementType = FILE
}