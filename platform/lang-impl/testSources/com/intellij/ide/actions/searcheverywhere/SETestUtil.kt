// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.Processor
import javax.swing.JPanel
import javax.swing.ListCellRenderer

fun createDumbContributor(id: String, showTab: Boolean = false, essential: Boolean = false): SearchEverywhereContributor<Unit> =
  object : SearchEverywhereContributor<Unit>, EssentialContributor {
    override fun getSearchProviderId(): String = id
    override fun getGroupName(): String = id
    override fun getSortWeight(): Int = 0
    override fun showInFindResults(): Boolean = false
    override fun isShownInSeparateTab(): Boolean = showTab
    override fun getElementsRenderer(): ListCellRenderer<in Unit> = ListCellRenderer { _, _, _, _, _ -> JPanel() }
    override fun processSelectedItem(selected: Unit, modifiers: Int, searchText: String): Boolean = false
    override fun fetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in Unit>) {}

    override fun isEssential(): Boolean = essential

    override fun toString(): String = id
  }