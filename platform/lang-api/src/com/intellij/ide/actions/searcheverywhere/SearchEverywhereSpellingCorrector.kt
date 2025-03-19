// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.lang.LangBundle
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Contract
import javax.swing.ListCellRenderer
import javax.swing.text.JTextComponent

@Internal
interface SearchEverywhereSpellingCorrector {
  companion object {
    private val EP_NAME = ExtensionPointName.create<SearchEverywhereSpellingCorrectorFactory>("com.intellij.searchEverywhereSpellingCorrector")

    @JvmStatic
    @Contract("null -> null")
    fun getInstance(project: Project?): SearchEverywhereSpellingCorrector? {
      if (project == null) return null

      val extensions = EP_NAME.extensionList
      return extensions.firstOrNull()
        ?.takeIf { it.isAvailable(project) }
        ?.create(project)
    }
  }

  fun isAvailableInTab(tabId: String): Boolean

  fun checkSpellingOf(query: String): SearchEverywhereSpellCheckResult
}

@Internal
interface SearchEverywhereSpellingCorrectorFactory {
  fun isAvailable(project: Project): Boolean

  fun create(project: Project): SearchEverywhereSpellingCorrector
}

@Internal
class SearchEverywhereSpellingCorrectorContributor(private val textComponent: JTextComponent) : SearchEverywhereContributor<SearchEverywhereSpellCheckResult.Correction> {
  override fun getSearchProviderId(): String = this::class.java.simpleName
  override fun getGroupName(): String = LangBundle.message("search.everywhere.typos.group.name")
  override fun getSortWeight(): Int = 0
  override fun showInFindResults(): Boolean = false
  override fun fetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in SearchEverywhereSpellCheckResult.Correction>) { }
  override fun processSelectedItem(selected: SearchEverywhereSpellCheckResult.Correction, modifiers: Int, searchText: String): Boolean {
    textComponent.text = selected.correction
    return false
  }
  override fun getElementsRenderer(): ListCellRenderer<in SearchEverywhereSpellCheckResult.Correction> = SearchEverywhereSpellingElementRenderer()
}