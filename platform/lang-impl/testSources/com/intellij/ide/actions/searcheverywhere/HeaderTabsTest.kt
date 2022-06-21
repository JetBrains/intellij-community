// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.Processor
import org.junit.Assert
import org.junit.Test
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class HeaderTabsTest {

  @Test
  fun contributorDefinedStrategyTest() {
    ContributorDefinedTabsCustomizationStrategy().doTest(
      mapOf(
        emptyList<Pair<String, Boolean>>() to emptyList(),
        listOf("c1" to true, "c2" to true, "c3" to false, "c4" to true, "c5" to false) to listOf("c1", "c2", "c4"),
        listOf("c1" to false, "c2" to false, "c3" to false, "c4" to false, "c5" to false) to emptyList()
        )
    )
  }

  @Test
  fun fixedListDefinedStrategyTest() {
    createFixedListStrategy(listOf("c1", "c2", "c5")).doTest(
      mapOf(
        emptyList<Pair<String, Boolean>>() to emptyList(),
        listOf("c1" to true, "c2" to true, "c3" to false, "c4" to true, "c5" to false) to listOf("c1", "c2"),
        listOf("c1" to false, "c2" to false, "c3" to false, "c4" to false, "c5" to false) to emptyList()
      )
    )

    createFixedListStrategy(emptyList()).doTest(
      mapOf(
        emptyList<Pair<String, Boolean>>() to emptyList(),
        listOf("c1" to true, "c2" to true, "c3" to false, "c4" to true, "c5" to false) to emptyList(),
        listOf("c1" to false, "c2" to false, "c3" to false, "c4" to false, "c5" to false) to emptyList()
      )
    )
  }

  private fun createFixedListStrategy(ids: List<String>): TabsCustomizationStrategy {
    return object : FixedTabsListCustomizationStrategy(ids) {}
  }

  private fun TabsCustomizationStrategy.doTest(map: Map<List<Pair<String, Boolean>>, List<String>>) {
    map.forEach { (contributors, expected) ->
      val actual = contributors
        .map { createDumbContributor(it.first, it.second) }
        .let { getSeparateTabContributors(it) }
        .map { it.searchProviderId }

      Assert.assertEquals(expected, actual)
    }
  }

  private fun createDumbContributor(id: String, showTab: Boolean): SearchEverywhereContributor<Unit> =
    object : SearchEverywhereContributor<Unit> {
      override fun getSearchProviderId(): String = id
      override fun getGroupName(): String = id
      override fun getSortWeight(): Int = 0
      override fun showInFindResults(): Boolean = false
      override fun isShownInSeparateTab(): Boolean = showTab
      override fun getElementsRenderer(): ListCellRenderer<in Unit> = ListCellRenderer { _, _, _, _, _ -> JPanel() }
      override fun getDataForItem(element: Unit, dataId: String): Any? = null
      override fun processSelectedItem(selected: Unit, modifiers: Int, searchText: String): Boolean = false
      override fun fetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in Unit>) {}
    }
}