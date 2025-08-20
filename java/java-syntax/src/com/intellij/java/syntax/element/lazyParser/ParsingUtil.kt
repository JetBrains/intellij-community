// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ParsingUtil")

package com.intellij.java.syntax.element.lazyParser

import com.intellij.java.syntax.JavaSyntaxBundle
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import kotlin.jvm.JvmName

inline fun parseFragment(
  builder: SyntaxTreeBuilder,
  type: SyntaxElementType,
  eatAll: Boolean = false,
  block: () -> Unit,
) {
  val root = builder.mark()
  block()
  if (!builder.eof()) {
    if (!eatAll) throw IncompleteFragmentParsingException("Unexpected token: '${builder.tokenText}'")
    val extras = builder.mark()
    while (!builder.eof()) {
      builder.advanceLexer()
    }
    extras.error(JavaSyntaxBundle.message("unexpected.tokens"))
  }
  root.done(type)
}

class IncompleteFragmentParsingException(message: String) : RuntimeException(message)