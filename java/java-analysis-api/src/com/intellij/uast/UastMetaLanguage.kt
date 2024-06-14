// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uast

import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.lang.Language
import com.intellij.lang.MetaLanguage
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.UastLanguagePlugin.Companion.extensionPointName
import org.jetbrains.uast.UastLanguagePlugin.Companion.getInstances
import kotlin.concurrent.Volatile

class UastMetaLanguage private constructor() : MetaLanguage("UAST") {
  internal object Holder {
    @Volatile
    var myLanguages: @Unmodifiable MutableSet<Language> = initLanguages()

    init {
      extensionPointName.addChangeListener(
        { myLanguages = initLanguages() }, null)
    }

    private fun initLanguages(): @Unmodifiable MutableSet<Language> {
      return java.util.Set.of(*ContainerUtil.map2Array(getInstances(), EMPTY_ARRAY) { plugin: UastLanguagePlugin -> plugin.language })
    }

    val languages: Set<Language>
      get() = myLanguages
  }

  override fun matchesLanguage(language: Language): Boolean {
    return Holder.myLanguages.contains(language)
  }

  override fun getMatchingLanguages(): Collection<Language> {
    return Holder.myLanguages
  }

  override fun getDisplayName(): String {
    return JavaAnalysisBundle.message("uast.language.display.name")
  }
}
