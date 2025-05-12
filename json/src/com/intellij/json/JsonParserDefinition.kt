// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.psi.impl.JsonFileImpl
import com.intellij.json.syntax.JsonLexer
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.psi.ElementTypeConverters.getConverter
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory.Companion.getInstance
import com.intellij.platform.syntax.psi.impl.getSyntaxParserRuntimeFactory
import com.intellij.platform.syntax.psi.lexer.LexerAdapter
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

@JvmField
val SYNTAX_FILE: SyntaxElementType = SyntaxElementType("FILE")
@JvmField
val FILE: IFileElementType = object : IFileElementType(JsonLanguage.INSTANCE) {
  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
    val builderFactory = getInstance()
    val elementType = chameleon.getElementType()
    val lexer = JsonLexer()
    val syntaxBuilder = builderFactory.createBuilder(chameleon,
                                                     lexer,
                                                     language,
                                                     chameleon.getChars())
    val runtimeParserRuntime =
      getSyntaxParserRuntimeFactory(language).buildParserRuntime(syntaxBuilder.getSyntaxTreeBuilder())
    val convertedElement = getConverter(language).convert(elementType)
    assert(convertedElement != null)
    JsonParser().parse(convertedElement!!, runtimeParserRuntime)
    return syntaxBuilder.getTreeBuilt().getFirstChildNode()
  }
}

open class JsonParserDefinition : ParserDefinition {

  override fun createLexer(project: Project?): Lexer {
    return LexerAdapter(JsonLexer(), getConverter(JsonLanguage.INSTANCE))
  }

  override fun createParser(project: Project?): PsiParser {throw UnsupportedOperationException("Should not be called directly")}

  override fun getFileNodeType(): IFileElementType {
    return FILE
  }

  override fun getCommentTokens(): TokenSet {
    return JsonTokenSets.JSON_COMMENTARIES
  }

  override fun getStringLiteralElements(): TokenSet {
    return JsonTokenSets.STRING_LITERALS
  }

  override fun createElement(astNode: ASTNode): PsiElement {
    return JsonElementTypes.Factory.createElement(astNode)
  }

  override fun createFile(fileViewProvider: FileViewProvider): PsiFile {
    return JsonFileImpl(fileViewProvider, JsonLanguage.INSTANCE)
  }

  override fun spaceExistenceTypeBetweenTokens(astNode: ASTNode?, astNode2: ASTNode?): ParserDefinition.SpaceRequirements {
    return ParserDefinition.SpaceRequirements.MAY
  }
}