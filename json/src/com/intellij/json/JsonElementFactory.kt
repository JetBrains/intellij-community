// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.psi.impl.JsonLazyObjectImpl
import com.intellij.json.syntax.JsonSyntaxLexer
import com.intellij.json.syntax.isObjectReparseable
import com.intellij.json.syntax.parseObject
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.tree.util.parents
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.performLexing
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
    "OBJECT" -> LAZY_OBJECT
    else -> throw IllegalArgumentException(name)
  }

  private val LAZY_OBJECT: IElementType = object : IReparseableElementType("OBJECT", JsonLanguage.INSTANCE) {
    override fun createNode(text: CharSequence?): ASTNode {
      return JsonLazyObjectImpl( text)
    }

    override fun isReparseable(currentNode: ASTNode, newText: CharSequence, fileLanguage: Language, project: Project): Boolean {
      val lexer = findLexer(fileLanguage)
      val cancellationProvider = CancellationProvider { ProgressManager.checkCanceled() }
      val tokenList = performLexing(newText, lexer, cancellationProvider, thisLogger().asSyntaxLogger())
      return isObjectReparseable(
        tokenList = tokenList,
        cancellationProvider = cancellationProvider
      )
    }

    override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
      val language = psi.containingFile.language // json or json5
      val lexer = findLexer(language)
      val syntaxBuilder = PsiSyntaxBuilderFactory.getInstance().createBuilder(chameleon, lexer, language, chameleon.getChars())
      val builder = syntaxBuilder.getSyntaxTreeBuilder()
      val deepLevel = chameleon.parents(false).count()
      return registerParse(builder, language) {
        parseObject(builder, deepLevel)
        syntaxBuilder.getTreeBuilt().getFirstChildNode()
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
}
