// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.Processor
import org.junit.Assert
import java.util.function.Function
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class SearchEverywhereTabsCustomizationTest : LightJavaCodeInsightFixtureTestCase() {

  private val contributors : List<SearchEverywhereContributor<*>> = listOf(
    dumb("c1", true),
    dumb("c2", true),
    dumb("c3", true),
    dumb("c4", false),
    dumb("c5", false)
  )

  private lateinit var ui : SearchEverywhereUI

  override fun setUp() {
    super.setUp()
    ui = SearchEverywhereUI(project, contributors).apply { Disposer.register(testRootDisposable, this) }
  }

  fun testFixedTabsListStrategy() {
    val strategy = object : FixedTabsListCustomizationStrategy(listOf("c1", "c3", "c5")) {}
    ApplicationManager.getApplication().replaceService(TabsCustomizationStrategy::class.java, strategy, testRootDisposable)
    val header = SearchEverywhereHeader(project, contributors, Runnable {  }, Function { _ -> null }, null, ui)
    val tabIDs = header.tabs.map { it.id }
    val expected = listOf(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID, "c1", "c3")
    Assert.assertEquals(expected, tabIDs)
  }

  fun testContributorDefinedStrategy() {
    val strategy = ContributorDefinedTabsCustomizationStrategy()
    ApplicationManager.getApplication().replaceService(TabsCustomizationStrategy::class.java, strategy, testRootDisposable)
    val header = SearchEverywhereHeader(project, contributors, Runnable {  }, Function { _ -> null }, null, ui)
    val tabIDs = header.tabs.map { it.id }
    val expected = listOf(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID, "c1", "c2", "c3")
    Assert.assertEquals(expected, tabIDs)
  }

  private fun dumb(id: String, showTab: Boolean): SearchEverywhereContributor<Unit> =
    object : SearchEverywhereContributor<Unit> {
      override fun getSearchProviderId(): String = id
      override fun getGroupName(): String = id
      override fun getSortWeight(): Int = 0
      override fun showInFindResults(): Boolean = false
      override fun isShownInSeparateTab(): Boolean = showTab
      override fun getElementsRenderer(): ListCellRenderer<in Unit> = ListCellRenderer { _, _, _, _, _ -> JPanel() }
      override fun processSelectedItem(selected: Unit, modifiers: Int, searchText: String): Boolean = false
      override fun fetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in Unit>) {}
    }
}