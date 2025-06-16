// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.frontback.psi.impl.syntax

import com.intellij.java.syntax.JavaSyntaxDefinition
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.LanguageSyntaxDefinition
import com.intellij.pom.java.LanguageLevel

internal class JavaSyntaxDefinitionExtension : LanguageSyntaxDefinition by JavaSyntaxDefinition