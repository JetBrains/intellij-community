// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.uast

import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.lang.Language
import com.intellij.lang.MetaLanguage
import com.intellij.lang.MetaLanguageProvider
import org.jetbrains.uast.UastLanguagePlugin.Companion.EP

internal fun getUastMetaLanguages(): Set<Language> {
  return EP.computeIfAbsent(UastMetaLanguage::class.java) { EP.lazySequence().mapTo(HashSet()) { it.language } }
}

public object UastMetaLanguage : MetaLanguage("UAST") {
  override fun matchesLanguage(language: Language): Boolean = getUastMetaLanguages().contains(language)

  override fun getMatchingLanguages(): Collection<Language> = getUastMetaLanguages()

  override fun getDisplayName(): String = JavaAnalysisBundle.message("uast.language.display.name")

  internal class Provider : MetaLanguageProvider {
    override fun getLanguage(): MetaLanguage = UastMetaLanguage
  }
}