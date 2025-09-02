// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser

import com.intellij.platform.syntax.psi.PsiSyntaxBuilder
import com.intellij.pom.java.LanguageLevel

class PsiSyntaxBuilderWithLanguageLevel(
  val builder: PsiSyntaxBuilder,
  val languageLevel: LanguageLevel,
)