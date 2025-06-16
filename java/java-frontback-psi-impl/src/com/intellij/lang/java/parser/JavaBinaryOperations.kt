// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser

import com.intellij.java.frontback.psi.impl.syntax.BasicJavaElementTypeConverter
import com.intellij.java.syntax.parser.ExpressionParser
import com.intellij.platform.syntax.psi.asTokenSet
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object JavaBinaryOperations {
  // todo replace BasicJavaElementTypeConverter with another Converter when parsers are merged
  @JvmField val ASSIGNMENT_OPS: TokenSet = ExpressionParser.ASSIGNMENT_OPS.asTokenSet(BasicJavaElementTypeConverter)
  @JvmField val SHIFT_OPS: TokenSet = ExpressionParser.SHIFT_OPS.asTokenSet(BasicJavaElementTypeConverter)
  @JvmField val ADDITIVE_OPS: TokenSet = ExpressionParser.ADDITIVE_OPS.asTokenSet(BasicJavaElementTypeConverter)
  @JvmField val MULTIPLICATIVE_OPS: TokenSet = ExpressionParser.MULTIPLICATIVE_OPS.asTokenSet(BasicJavaElementTypeConverter)
}