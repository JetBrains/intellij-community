// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaSyntaxUtil")

package com.intellij.java.syntax.element

import com.intellij.java.syntax.JavaSyntaxDefinition
import com.intellij.java.syntax.JavaSyntaxLog
import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.lexer.performLexing
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory
import com.intellij.platform.syntax.tree.SyntaxNode
import com.intellij.pom.java.LanguageLevel
import kotlin.jvm.JvmName

internal fun createSyntaxBuilder(
  chameleon: SyntaxNode,
  text: CharSequence,
  languageLevel: LanguageLevel,
  cachedLexemes: TokenList? = null,
  cancellationProvider: CancellationProvider?,
): SyntaxTreeBuilder {
  val lexemes = cachedOrLex(cachedLexemes, text, languageLevel, cancellationProvider)
  val builder = SyntaxTreeBuilderFactory.builder(
    text = text,
    whitespaces = JavaSyntaxDefinition.whitespaces,
    comments = JavaSyntaxDefinition.commentSet,
    tokenList = lexemes,
  )
    .withStartOffset(chameleon.startOffset)
    .withLanguage(JavaSyntaxDefinition.language.id)
    .withWhitespaceOrCommentBindingPolicy(JavaBindingPolicy)
    .withCancellationProvider(cancellationProvider)
    .withLogger(JavaSyntaxLog.log)

  return builder.build()
}

internal fun cachedOrLex(
  cachedLexemes: TokenList?,
  text: CharSequence,
  languageLevel: LanguageLevel,
  cancellationProvider: CancellationProvider?,
): TokenList {
  return cachedLexemes ?: run {
    val lexer = JavaSyntaxDefinition.createLexer(languageLevel)
    performLexing(text, lexer, cancellationProvider, JavaSyntaxLog.log)
  }
}
