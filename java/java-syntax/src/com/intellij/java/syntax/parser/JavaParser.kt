// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.parser

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
open class JavaParser(val languageLevel: LanguageLevel) {
  // todo make abstract and add a separate implementation?
  open val fileParser: FileParser = FileParser(this)
  open val moduleParser: ModuleParser = ModuleParser(this)
  open val declarationParser: DeclarationParser = DeclarationParser(this)
  open val statementParser: StatementParser = StatementParser(this)
  open val expressionParser: ExpressionParser = ExpressionParser(PrattExpressionParser(this))
  open val referenceParser: ReferenceParser = ReferenceParser(this)
  open val patternParser: PatternParser = PatternParser(this)
}