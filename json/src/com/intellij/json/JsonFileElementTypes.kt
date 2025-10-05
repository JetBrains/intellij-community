// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.json5.Json5Language
import com.intellij.json.jsonLines.JsonLinesLanguage
import com.intellij.json.syntax.JsonSyntaxLexer
import com.intellij.json.syntax.JsonSyntaxParser
import com.intellij.json.syntax.json5.Json5SyntaxLexer
import com.intellij.lang.ASTNode
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.psi.ElementTypeConverters
import com.intellij.platform.syntax.psi.ElementTypeConverters.getConverter
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory.Companion.getInstance
import com.intellij.platform.syntax.psi.impl.getSyntaxParserRuntimeFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IFileElementType

@JvmField
val JSON_SYNTAX_FILE: SyntaxElementType = SyntaxElementType("JSON_FILE")
@JvmField
val JSON_FILE: IFileElementType = object : IFileElementType(JsonLanguage.INSTANCE) {
  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
    val builderFactory = getInstance()
    val elementType = chameleon.getElementType()
    val lexer = JsonSyntaxLexer()
    val syntaxBuilder = builderFactory.createBuilder(chameleon,
                                                     lexer,
                                                     language,
                                                     chameleon.getChars())
    val runtimeParserRuntime =
      getSyntaxParserRuntimeFactory(language).buildParserRuntime(syntaxBuilder.getSyntaxTreeBuilder())
    val convertedElement = getConverter(language).convert(elementType)
    assert(convertedElement != null) { "Failed convert element type: $elementType" }
    JsonSyntaxParser().parse(convertedElement!!, runtimeParserRuntime)
    return syntaxBuilder.getTreeBuilt().getFirstChildNode()
  }
}


@JvmField
val JSON5_SYNTAX_FILE: SyntaxElementType = SyntaxElementType("JSON5_FILE")

@JvmField
val JSON5_FILE: IFileElementType = object : IFileElementType(Json5Language.INSTANCE) {
  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
    val builderFactory = PsiSyntaxBuilderFactory.Companion.getInstance()
    val elementType = chameleon.getElementType()
    val lexer = Json5SyntaxLexer()
    val syntaxBuilder = builderFactory.createBuilder(chameleon,
                                                     lexer,
                                                     language,
                                                     chameleon.getChars())
    val runtimeParserRuntime =
      getSyntaxParserRuntimeFactory(Json5Language.INSTANCE).buildParserRuntime(syntaxBuilder.getSyntaxTreeBuilder())
    val convertedElement = getConverter(language).convert(elementType)
    assert(convertedElement != null) { "Failed convert element type: $elementType" }
    JsonSyntaxParser().parse(convertedElement!!, runtimeParserRuntime)

    return syntaxBuilder.getTreeBuilt().getFirstChildNode()
  }
}

@JvmField
val JSON_LINES_SYNTAX_FILE: SyntaxElementType = SyntaxElementType("JSON_LINES_FILE")

@JvmField
val JSON_LINES_FILE: IFileElementType = object : IFileElementType(JsonLinesLanguage) {
  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
    val builderFactory = getInstance()
    val elementType = chameleon.getElementType()
    val lexer = JsonSyntaxLexer()
    val syntaxBuilder = builderFactory.createBuilder(chameleon,
                                                     lexer,
                                                     language,
                                                     chameleon.getChars())
    val runtimeParserRuntime =
      getSyntaxParserRuntimeFactory(Json5Language.INSTANCE).buildParserRuntime(syntaxBuilder.getSyntaxTreeBuilder())

    JsonSyntaxParser().parse(ElementTypeConverters.getConverter(language).convert(elementType)!!, runtimeParserRuntime)
    return syntaxBuilder.getTreeBuilt().getFirstChildNode()
  }
}