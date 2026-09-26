// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION") // the old Search Everywhere API still backs the split SE through the adapter

package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.Processor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.swing.Icon
import javax.swing.ListCellRenderer

@TestApplication
class PSIPresentationBgRendererWrapperTest {

  @Test
  fun `broken item presentation does not abort the fetch`(@TestDisposable disposable: Disposable) {
    val broken = brokenItem()
    val wrapper = PSIPresentationBgRendererWrapper(contributorWith(disposable, item("before"), broken, item("after")))

    val collected = mutableListOf<FoundItemDescriptor<Any>>()
    wrapper.fetchWeightedElements("pattern", EmptyProgressIndicator(), Processor { collected.add(it) })

    val texts = collected.map { (it.item as PSIPresentationBgRendererWrapper.ItemWithPresentation<*>).presentation.presentableText }
    assertEquals(listOf("before", broken.toString(), "after"), texts)
  }

  private fun item(text: String): NavigationItem = object : NavigationItem {
    override fun getName(): String = text
    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
      override fun getPresentableText(): String = text
      override fun getLocationString(): String? = null
      override fun getIcon(unused: Boolean): Icon? = null
    }
  }

  private fun brokenItem(): NavigationItem = object : NavigationItem {
    override fun getName(): String = "broken"
    override fun getPresentation(): ItemPresentation = throw IllegalStateException("presentation failure")
    override fun toString(): String = "broken"
  }

  private fun contributorWith(disposable: Disposable, vararg items: Any): WeightedSearchEverywhereContributor<Any> =
    object : WeightedSearchEverywhereContributor<Any> {
      override fun getSearchProviderId(): String = "TestContributor"
      override fun getGroupName(): String = "Test"
      override fun getSortWeight(): Int = 0
      override fun showInFindResults(): Boolean = false
      override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean = false
      override fun getElementsRenderer(): ListCellRenderer<in Any> = SearchEverywherePsiRenderer(disposable)

      override fun fetchWeightedElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in FoundItemDescriptor<Any>>,
      ) {
        items.forEachIndexed { index, item -> consumer.process(FoundItemDescriptor(item, 100 - index)) }
      }
    }
}
