// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.psi.impl.JsonLazyArrayImpl
import com.intellij.json.psi.impl.JsonLazyObjectImpl
import com.intellij.json.syntax.JsonBlockChecker
import com.intellij.json.syntax.JsonLazyParsing
import com.intellij.json.syntax.JsonSyntaxElementTypes
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
import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.Logger
import com.intellij.platform.syntax.SyntaxElementType
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
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.LazyParseableElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IReparseableElementType

object JsonElementFactory {
  @JvmStatic
  fun getType(name: String): IElementType = when (name) {
    "OBJECT" -> if (JsonLazyParsing) LAZY_OBJECT else OBJECT
    "ARRAY" -> if (JsonLazyParsing) LAZY_ARRAY else ARRAY
    else -> throw IllegalArgumentException(name)
  }

  /** Returns the token type of [name]. A string, number, identifier or comment token can get a new text without a reparse of the file. */
  @JvmStatic
  fun getTokenType(name: String): IElementType = when (name) {
    "DOUBLE_QUOTED_STRING" -> JsonReparseableTokenType(name, JsonSyntaxElementTypes.DOUBLE_QUOTED_STRING)
    "SINGLE_QUOTED_STRING" -> JsonReparseableTokenType(name, JsonSyntaxElementTypes.SINGLE_QUOTED_STRING)
    "NUMBER" -> JsonReparseableTokenType(name, JsonSyntaxElementTypes.NUMBER)
    "IDENTIFIER" -> JsonReparseableTokenType(name, JsonSyntaxElementTypes.IDENTIFIER)
    "LINE_COMMENT" -> JsonReparseableTokenType(name, JsonSyntaxElementTypes.LINE_COMMENT)
    "BLOCK_COMMENT" -> JsonReparseableTokenType(name, JsonSyntaxElementTypes.BLOCK_COMMENT)
    else -> JsonTokenType(name)
  }

  private val LAZY_OBJECT: IElementType = lazyElementType(
    name = "OBJECT",
    createNode = { JsonLazyObjectImpl(it) },
    open = JsonSyntaxElementTypes.L_CURLY,
    isReparseable = { tokenList, cancellationProvider -> isObjectReparseable(tokenList, cancellationProvider) },
    parseContents = { builder, deepLevel -> parseObject(builder, deepLevel) },
  )

  private val LAZY_ARRAY: IElementType = lazyElementType(
    name = "ARRAY",
    createNode = { JsonLazyArrayImpl(it) },
    open = JsonSyntaxElementTypes.L_BRACKET,
    isReparseable = { tokenList, cancellationProvider -> isArrayReparseable(tokenList, cancellationProvider) },
    parseContents = { builder, deepLevel -> parseArray(builder, deepLevel) },
  )

  private val OBJECT: IElementType = JsonElementType("OBJECT")
  private val ARRAY: IElementType = JsonElementType("ARRAY")

  private inline fun lazyElementType(
    name: String,
    crossinline createNode: (CharSequence?) -> ASTNode,
    open: SyntaxElementType,
    crossinline isReparseable: (TokenList, CancellationProvider) -> Boolean,
    crossinline parseContents: (SyntaxTreeBuilder, Int) -> ProductionResult,
  ): IElementType = object : IReparseableElementType(name, JsonLanguage.INSTANCE) {
    override fun createNode(text: CharSequence?): ASTNode = createNode(text)

    /** Checks the new text and the old text of [currentNode]. */
    override fun isReparseable(currentNode: ASTNode, newText: CharSequence, fileLanguage: Language, project: Project): Boolean {
      val lexer = findLexer(jsonDialectOf(currentNode.psi, fileLanguage))
      val cancellationProvider = CancellationProvider { ProgressManager.checkCanceled() }
      val logger = thisLogger().asSyntaxLogger()
      return isReparseable(performLexing(newText, lexer, cancellationProvider, logger), cancellationProvider) &&
             isOldTextBlock(currentNode, JsonBlockChecker(open, cancellationProvider), lexer, cancellationProvider, logger)
    }

    override fun getLanguageForParser(psi: PsiElement): Language = jsonDialectOf(psi, JsonLanguage.INSTANCE)

    override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
      val language = getLanguageForParser(psi)
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

  /**
   * Gives the tokens of the old text of [root] to [checker], and returns [JsonBlockChecker.isBlock].
   *
   * A parsed node gives its leaves. An unparsed node gives the tokens of its cached text, so the walk copies no text.
   */
  private fun isOldTextBlock(
    root: ASTNode,
    checker: JsonBlockChecker,
    lexer: Lexer,
    cancellationProvider: CancellationProvider,
    logger: Logger,
  ): Boolean {
    var node = root
    while (true) {
      if (node is LazyParseableElement && !node.isParsed) {
        val tokens = performLexing(node.chars, lexer, cancellationProvider, logger)
        for (i in 0 until tokens.tokenCount) {
          if (!checker.add(tokens.getTokenType(i))) return false
        }
      }
      else if (node !is CompositeElement) {
        if (!checker.add(braceOf(node))) return false
      }
      else {
        val firstChild = node.firstChildNode
        if (firstChild != null) {
          node = firstChild
          continue
        }
      }
      while (node !== root && node.treeNext == null) {
        node = node.treeParent
      }
      if (node === root) return checker.isBlock
      node = node.treeNext
    }
  }

  private fun braceOf(leaf: ASTNode): SyntaxElementType? = when (leaf.elementType) {
    JsonElementTypes.L_CURLY -> JsonSyntaxElementTypes.L_CURLY
    JsonElementTypes.R_CURLY -> JsonSyntaxElementTypes.R_CURLY
    JsonElementTypes.L_BRACKET -> JsonSyntaxElementTypes.L_BRACKET
    JsonElementTypes.R_BRACKET -> JsonSyntaxElementTypes.R_BRACKET
    else -> null
  }

  /**
   * Returns the JSON dialect to lex and to parse with.
   *
   * A host language embeds a JSON fragment. The fragment then reports the language of the host file.
   * That language is no JSON dialect, so use plain JSON for it.
   *
   * @param psi the element to take the dialect from, or null if the tree has no PSI yet
   * @param fallback the language to use if [psi] is null
   */
  private fun jsonDialectOf(psi: PsiElement?, fallback: Language): Language =
    if (psi != null) JsonDialectUtil.getLanguageOrDefaultJson(psi)
    else fallback as? JsonLanguage ?: JsonLanguage.INSTANCE

  /** Returns the lexer of [fileLanguage], or a plain JSON lexer with an error log if the language has no syntax definition. */
  internal fun findLexer(fileLanguage: Language): Lexer {
    val definition = LanguageSyntaxDefinitions.INSTANCE.forLanguage(fileLanguage) ?: run {
      thisLogger().error("No syntax definition found for language $fileLanguage")
      return JsonSyntaxLexer()
    }

    return definition.createLexer()
  }
}
