// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5

import com.intellij.json.JsonParser
import com.intellij.json.JsonParserDefinition
import com.intellij.json.psi.impl.JsonFileImpl
import com.intellij.lang.ASTNode
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.psi.ElementTypeConverters.getConverter
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory
import com.intellij.platform.syntax.psi.impl.getSyntaxParserRuntimeFactory
import com.intellij.platform.syntax.psi.lexer.LexerAdapter
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType

@JvmField
val SYNTAX_FILE: SyntaxElementType = SyntaxElementType("FILE")

@JvmField
val FILE: IFileElementType = object : IFileElementType(Json5Language.INSTANCE) {
  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
    val builderFactory = PsiSyntaxBuilderFactory.Companion.getInstance()
    val elementType = chameleon.getElementType()
    val lexer = Json5Lexer()
    val syntaxBuilder = builderFactory.createBuilder(chameleon,
                                                     lexer,
                                                     language,
                                                     chameleon.getChars())
    val runtimeParserRuntime =
      getSyntaxParserRuntimeFactory(Json5Language.INSTANCE).buildParserUtils(syntaxBuilder.getSyntaxTreeBuilder())
    val convertedElement = getConverter(language).convert(elementType)
    assert(convertedElement != null)
    JsonParser().parse(convertedElement!!, runtimeParserRuntime)
    
    return syntaxBuilder.getTreeBuilt().getFirstChildNode()
  }
}

class Json5ParserDefinition : JsonParserDefinition() {
  
  public override fun createLexer(project: Project?): Lexer {
    return LexerAdapter(Json5Lexer(), getConverter(Json5Language.INSTANCE))
  }

  public override fun createFile(fileViewProvider: FileViewProvider): PsiFile {
    return JsonFileImpl(fileViewProvider, Json5Language.INSTANCE)
  }

  public override fun getFileNodeType(): IFileElementType {
    return FILE
  }
}