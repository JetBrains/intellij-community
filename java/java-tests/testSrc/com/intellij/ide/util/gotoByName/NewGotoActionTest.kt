// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

//import com.intellij.platform.searchEverywhere.frontend.dispatcher.SearchEverywhereDispatcher
//import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereItemDataLocalProvider
//import com.intellij.platform.searchEverywhere.testFramework.SearchEverywhereItemMock
//import com.intellij.platform.searchEverywhere.testFramework.SearchEverywhereSessionHelperMock
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionsProvider
import com.intellij.platform.searchEverywhere.providers.mocks.SeItemsProviderMock
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class NewGotoActionTest: LightJavaCodeInsightFixtureTestCase() {
  private val defaultProvider: SeItemsProvider get() = SeItemsProviderMock(delayMillis = 1000, delayStep = 5, displayName = "Default")

  fun `test empty`() {}

  @Suppress("unused")
  fun `mock test simple search`() {
    runBlocking {
      val params = SeParams("apply patch", SeFilterState.Empty)

      SeActionsProvider(project, null, null).collectItems(params, Collector { item ->
        println(item.presentation().text)
        true
      })
    }
  }

  @Suppress("unused")
  fun `mock test mocked provider`() {
    runBlocking {
      val params = SeParams("it", SeFilterState.Empty)

      defaultProvider.collectItems(params, Collector { item ->
        println(item.presentation().text)
        true
      })
    }
  }

  //@Suppress("unused")
  //fun `mock test search dispatcher`() {
  //  val providers = listOf(
  //    SearchEverywhereItemsProviderMock(delayMillis = 100, delayStep = 1, resultPrefix = "alpha"),
  //    SearchEverywhereItemsProviderMock(delayMillis = 1000, delayStep = 5, resultPrefix = "bravo"),
  //    SearchEverywhereItemsProviderMock(delayMillis = 1500, delayStep = 7, resultPrefix = "charlie"),
  //    SearchEverywhereItemsProviderMock(delayMillis = 2000, delayStep = 10, resultPrefix = "delta"),
  //  ).map {
  //    SearchEverywhereItemDataLocalProvider(it)
  //  }
  //
  //  runBlocking {
  //    val searchJob = launch {
  //      val params = SearchEverywhereTextSearchParams("item")
  //      SearchEverywhereDispatcher(providers, emptyMap()).getItems(params, emptyList()).collect {
  //        println(it.presentation.text)
  //      }
  //    }
  //
  //    searchJob.invokeOnCompletion {
  //      if (searchJob.isCancelled) {
  //        println("Search cancelled")
  //      }
  //      else {
  //        println("Search completed")
  //      }
  //    }
  //
  //    delay(5000)
  //    println("Canceling search")
  //    searchJob.cancel()
  //    searchJob.join()
  //  }
  //}

  //@Suppress("unused")
  //fun `mock test search dispatcher with limits`() {
  //  val providers = listOf(
  //    SearchEverywhereItemsProviderMock(delayMillis = 100, delayStep = 1, resultPrefix = "alpha"),
  //    SearchEverywhereItemsProviderMock(delayMillis = 1000, delayStep = 5, resultPrefix = "bravo")
  //  ).map {
  //    SearchEverywhereItemDataLocalProvider(it)
  //  }
  //
  //  val providersWithLimits = providers.mapIndexed { index, searchEverywhereViewItemsProvider ->
  //    searchEverywhereViewItemsProvider.id to (index + 1) * 3
  //  }.toMap()
  //
  //
  //  val existingResults = listOf("alpha 1" to providers[0],
  //                               "bravo 2" to providers[1],
  //                               "bravo 5" to providers[1]).map { (itemText, provider) ->
  //    val item = SearchEverywhereItemMock(itemText)
  //    val itemId = runBlocking { session.saveItem(item) }
  //    SearchEverywhereItemData(itemId, provider.id, weight = 0, ActionItemPresentation(text = itemText))
  //  }
  //
  //  runBlocking {
  //    val searchJob = launch {
  //      println("Search will start")
  //      SearchEverywhereDispatcher(providers, providersWithLimits).getItems(SearchEverywhereTextSearchParams("item"), existingResults).collect {
  //        println(it.presentation.text)
  //      }
  //    }
  //
  //    searchJob.invokeOnCompletion {
  //      if (searchJob.isCancelled) {
  //        println("Search cancelled")
  //      }
  //      else {
  //        println("Search completed")
  //      }
  //    }
  //
  //    delay(5000)
  //    println("Canceling search")
  //    searchJob.cancel()
  //    searchJob.join()
  //  }
  //}

  private class Collector(val collect: suspend (SeItem) -> Boolean): SeItemsProvider.Collector {
    override suspend fun put(item: SeItem): Boolean {
      return collect(item)
    }
  }
}