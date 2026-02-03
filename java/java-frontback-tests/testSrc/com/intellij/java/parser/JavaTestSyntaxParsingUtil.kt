// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("JavaTestSyntaxParsingUtil")

package com.intellij.java.parser

import com.intellij.java.syntax.JavaSyntaxDefinition
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.tree.KmpSyntaxNode

import com.intellij.platform.syntax.tree.SyntaxNode
import com.intellij.platform.syntax.tree.children
import com.intellij.platform.syntax.tree.parse
import com.intellij.platform.syntax.util.language.SyntaxElementLanguageProvider

internal fun parseForSyntaxTree(
  text: String,
  parser: ((SyntaxTreeBuilder) -> Unit),
): KmpSyntaxNode {
  val mapper = SyntaxElementLanguageProvider {
    sequenceOf(JavaSyntaxDefinition.language)
  }

  val tree = parse(
    text = text,
    lexerFactory = JavaSyntaxDefinition::createLexer,
    parser = parser,
    whitespaces = JavaSyntaxDefinition.whitespaces,
    comments = JavaSyntaxDefinition.comments,
    languageMapper = mapper,
    cancellationProvider = null,
    logger = null,
    whitespaceOrCommentBindingPolicy = JavaSyntaxDefinition.whitespaceOrCommentBindingPolicy,
  )

  return tree
}

internal fun dumpTree(node: SyntaxNode): String {
  val all = mutableListOf<SyntaxNode>()

  fun append(node: SyntaxNode) {
    all.add(node)
    for (child in node.children()) {
      append(child)
    }
  }
  append(node)

  val parsing = all.joinToString("\n") { node ->
    val type = node.type
    val prefix = " ".repeat((node.level()) * 2)
    "$prefix$type"
  }
  return parsing
}

private fun SyntaxNode.level(): Int = generateSequence(this) { it.parent() }.count() - 1

/**
 * remove `<empty list>` lines from the expected tree
 * kmp tree cannot have them because "composite/non-composite" node information is missing in the KMP tree
 */
internal fun String.removeEmptyListLines(): String =
  lines()
    .filterNot { it.trim() == "<empty list>" }
    .joinToString("\n")
