// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.LazyParsingContext
import com.intellij.platform.syntax.extensions.ExtensionPointKey
import com.intellij.platform.syntax.extensions.currentExtensionSupport
import com.intellij.platform.syntax.tree.SyntaxNode
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.annotations.ApiStatus

/**
 * Implement this extension point to provide the custom language level for Java lazy parsers.
 */
@ApiStatus.OverrideOnly
interface JavaLanguageLevelProvider {
  fun getLanguageLevel(node: SyntaxNode): LanguageLevel
}

internal fun getLanguageLevel(parsingContext: LazyParsingContext): LanguageLevel {
  val languageLevelProvider = currentExtensionSupport().getExtensions(languageLevelExtensionPoint).firstOrNull()
  val languageLevel = languageLevelProvider?.getLanguageLevel(parsingContext.node) ?: LanguageLevel.HIGHEST
  return languageLevel
}

private val languageLevelExtensionPoint: ExtensionPointKey<JavaLanguageLevelProvider> = ExtensionPointKey("com.intellij.java.syntax.languageLevelProvider")
