// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.psi.impl.JsonLazyArrayImpl
import com.intellij.json.psi.impl.JsonLazyObjectImpl
import com.intellij.json.syntax.JsonLazyParsing
import com.intellij.json.syntax.JsonSyntaxLexer
import com.intellij.json.syntax.isArrayReparseable
import com.intellij.json.syntax.isObjectReparseable
import com.intellij.json.syntax.parseArray
import com.intellij.json.syntax.parseObject
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.tree.util.parents
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.lexer.performLexing
import com.intellij.platform.syntax.parser.ProductionResult
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.psi.LanguageSyntaxDefinitions
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory
import com.intellij.platform.syntax.psi.asSyntaxLogger
import com.intellij.platform.syntax.psi.registerParse
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IReparseableElementType

object JsonElementFactory {
  @JvmStatic
  fun getType(name: String): IElementType = when (name) {
    "OBJECT" -> if (JsonLazyParsingIJ) LAZY_OBJECT else OBJECT
    "ARRAY" -> if (JsonLazyParsingIJ) LAZY_ARRAY else ARRAY
    else -> throw IllegalArgumentException(name)
  }

  /**
   * Handles lazy parsing switch of JSON files in IntelliJ platform.
   *
   * @see JsonLazyParsing
   */
  @JvmStatic
  val JsonLazyParsingIJ: Boolean =
    JsonLazyParsing || Registry.get("json.lazy.parsing").isBoolean

  private val LAZY_OBJECT: IElementType = lazyElementType(
    name = "OBJECT",
    createNode = { JsonLazyObjectImpl(it) },
    isReparseable = { tokenList, cancellationProvider -> isObjectReparseable(tokenList, cancellationProvider) },
    parseContents = { builder, deepLevel -> parseObject(builder, deepLevel) },
  )

  private val LAZY_ARRAY: IElementType = lazyElementType(
    name = "ARRAY",
    createNode = { JsonLazyArrayImpl(it) },
    isReparseable = { tokenList, cancellationProvider -> isArrayReparseable(tokenList, cancellationProvider) },
    parseContents = { builder, deepLevel -> parseArray(builder, deepLevel) },
  )

  private val OBJECT: IElementType = JsonElementType("OBJECT")
  private val ARRAY: IElementType = JsonElementType("ARRAY")

  private inline fun lazyElementType(
    name: String,
    crossinline createNode: (CharSequence?) -> ASTNode,
    crossinline isReparseable: (TokenList, CancellationProvider) -> Boolean,
    crossinline parseContents: (SyntaxTreeBuilder, Int) -> ProductionResult,
  ): IElementType = object : IReparseableElementType(name, JsonLanguage.INSTANCE) {
    override fun createNode(text: CharSequence?): ASTNode = createNode(text)

    override fun isReparseable(currentNode: ASTNode, newText: CharSequence, fileLanguage: Language, project: Project): Boolean {
      val lexer = findLexer(fileLanguage)
      val cancellationProvider = CancellationProvider { ProgressManager.checkCanceled() }
      val tokenList = performLexing(newText, lexer, cancellationProvider, thisLogger().asSyntaxLogger())
      return isReparseable(tokenList, cancellationProvider)
    }

    override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
      val language = psi.containingFile.language // json or json5
      val lexer = findLexer(language)
      val syntaxBuilder = PsiSyntaxBuilderFactory.getInstance().createBuilder(chameleon, lexer, language, chameleon.getChars())
      val builder = syntaxBuilder.getSyntaxTreeBuilder()
      val deepLevel = chameleon.parents(false).count()
      return registerParse(builder, language) {
        parseContents(builder, deepLevel)
        syntaxBuilder.getTreeBuilt().getFirstChildNode()
      }
    }
  }

  private fun findLexer(fileLanguage: Language): Lexer {
    val definition = LanguageSyntaxDefinitions.INSTANCE.forLanguage(fileLanguage) ?: run {
      thisLogger().error("No syntax definition found for language $fileLanguage")
      return JsonSyntaxLexer()
    }

    return definition.createLexer()
  }
}
