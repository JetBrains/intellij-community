// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.searchEverywhere.*
import com.intellij.searchEverywhere.core.DefaultSearchEverywhereDispatcher
import com.intellij.searchEverywhere.core.DefaultViewItemsProvider
import com.intellij.searchEverywhere.mocks.SearchEverywhereItemsProviderMock
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NewGotoActionTest: LightJavaCodeInsightFixtureTestCase() {
  fun `test empty`() {}

  @Suppress("unused")
  fun `mock test simple search`() {
    runBlocking {
      val provider = createProvider()
      val params = ActionSearchParams("apply patch", true)

      provider.processViewItems(params).collect {
        println(it)
      }
    }
  }

  @Suppress("unused")
  fun `mock test mocked provider`() {
    runBlocking {
      val params = "it"

      createMockProviders().first().processViewItems(params).collect {
        println(it.item)
      }
    }
  }

  @Suppress("unused")
  fun `mock test search dispatcher`() {
    val providers = createMockProviders(listOf(
      SearchEverywhereItemsProviderMock(delayMillis = 100, delayStep = 1, resultPrefix = "alpha"),
      SearchEverywhereItemsProviderMock(delayMillis = 1000, delayStep = 5, resultPrefix = "bravo"),
      SearchEverywhereItemsProviderMock(delayMillis = 1500, delayStep = 7, resultPrefix = "centurion"),
      SearchEverywhereItemsProviderMock(delayMillis = 2000, delayStep = 10, resultPrefix = "delta"),
    ))

    runBlocking {
      val searchJob = launch {
        DefaultSearchEverywhereDispatcher().search(providers, emptyMap(), "item", emptyList()).collect {
          println(it.item)
        }
      }

      searchJob.invokeOnCompletion {
        if (searchJob.isCancelled) {
          println("Search cancelled")
        }
        else {
          println("Search completed")
        }
      }

      delay(5000)
      println("Canceling search")
      searchJob.cancel()
      searchJob.join()
    }
  }

  @Suppress("unused")
  fun `mock test search dispatcher with limits`() {
    val providers = createMockProviders(listOf(
      SearchEverywhereItemsProviderMock(delayMillis = 100, delayStep = 1, resultPrefix = "alpha"),
      SearchEverywhereItemsProviderMock(delayMillis = 1000, delayStep = 5, resultPrefix = "bravo")
    ))

    val providersWithLimits = providers.mapIndexed { index, searchEverywhereViewItemsProvider ->
      (searchEverywhereViewItemsProvider as SearchEverywhereViewItemsProvider<*, *, String>) to (index + 1) * 3
    }.toMap()

    val existingResults = listOf("alpha 1", "bravo 2", "bravo 5").map {
      SearchEverywhereViewItem(it, OptionItemPresentation(name = it), weight = 0, dataContext = SimpleDataContext.getProjectContext(project))
    }

    runBlocking {
      val searchJob = launch {
        println("Search will start")
        DefaultSearchEverywhereDispatcher().search(providers, providersWithLimits, "item", existingResults).collect {
          println(it.item)
        }
      }

      searchJob.invokeOnCompletion {
        if (searchJob.isCancelled) {
          println("Search cancelled")
        }
        else {
          println("Search completed")
        }
      }

      searchJob.join()
    }
  }

  private fun createProvider(): SearchEverywhereViewItemsProvider<GotoActionModel.MatchedValue, SearchEverywhereItemPresentation, ActionSearchParams> {
    val itemsProvider = ActionsItemsProvider(project, null, null)
    return DefaultViewItemsProvider<GotoActionModel.MatchedValue, SearchEverywhereItemPresentation, ActionSearchParams>(
      itemsProvider,
      ActionPresentationProvider,
      { SimpleDataContext.getProjectContext(project) }
    )
  }

  private fun createMockProviders(
    itemProviders: List<SearchEverywhereItemsProvider<String, String>> =
      listOf(SearchEverywhereItemsProviderMock(delayMillis = 1000, delayStep = 5)),
  ): List<SearchEverywhereViewItemsProvider<String, OptionItemPresentation, String>> {
    return itemProviders.map { itemsProvider ->
      DefaultViewItemsProvider(
        itemsProvider,
        { item -> OptionItemPresentation(name = item) },
        { SimpleDataContext.getProjectContext(project) }
      )
    }
  }
}