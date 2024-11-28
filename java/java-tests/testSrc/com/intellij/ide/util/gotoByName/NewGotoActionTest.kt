// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.platform.searchEverywhere.ActionItemPresentation
import com.intellij.platform.searchEverywhere.ActionSearchParams
import com.intellij.platform.searchEverywhere.SearchEverywhereItemsProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereTextSearchParams
import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereFrontendDispatcher
import com.intellij.platform.searchEverywhere.testFramework.SearchEverywhereItemDataMock
import com.intellij.platform.searchEverywhere.testFramework.SearchEverywhereItemMock
import com.intellij.platform.searchEverywhere.testFramework.SearchEverywhereItemsProviderMock
import com.intellij.platform.searchEverywhere.testFramework.SearchEverywhereSessionMock
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NewGotoActionTest: LightJavaCodeInsightFixtureTestCase() {
  private val defaultProvider: SearchEverywhereItemsProvider get() = SearchEverywhereItemsProviderMock(delayMillis = 1000, delayStep = 5)

  fun `test empty`() {}

  @Suppress("unused")
  fun `mock test simple search`() {
    runBlocking {
      val session = SearchEverywhereSessionMock()
      val params = ActionSearchParams("apply patch", session, true)

      ActionsItemsProvider(project, null, null).getItems(params).collect {
        println(it.presentation.text)
      }
    }
  }

  @Suppress("unused")
  fun `mock test mocked provider`() {
    runBlocking {
      val params = SearchEverywhereTextSearchParams("it", SearchEverywhereSessionMock())

      defaultProvider.getItems(params).collect {
        println(it.presentation.text)
      }
    }
  }

  @Suppress("unused")
  fun `mock test search dispatcher`() {
    val providers = listOf(
      SearchEverywhereItemsProviderMock(delayMillis = 100, delayStep = 1, resultPrefix = "alpha"),
      SearchEverywhereItemsProviderMock(delayMillis = 1000, delayStep = 5, resultPrefix = "bravo"),
      SearchEverywhereItemsProviderMock(delayMillis = 1500, delayStep = 7, resultPrefix = "charlie"),
      SearchEverywhereItemsProviderMock(delayMillis = 2000, delayStep = 10, resultPrefix = "delta"),
    )

    runBlocking {
      val searchJob = launch {

        val params = SearchEverywhereTextSearchParams("item", SearchEverywhereSessionMock())
        SearchEverywhereFrontendDispatcher().getItems(params, providers, emptyMap(), emptyList()).collect {
          println(it.presentation.text)
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
    val providers = listOf(
      SearchEverywhereItemsProviderMock(delayMillis = 100, delayStep = 1, resultPrefix = "alpha"),
      SearchEverywhereItemsProviderMock(delayMillis = 1000, delayStep = 5, resultPrefix = "bravo")
    )

    val providersWithLimits = providers.mapIndexed { index, searchEverywhereViewItemsProvider ->
      searchEverywhereViewItemsProvider.id to (index + 1) * 3
    }.toMap()

    val session = SearchEverywhereSessionMock()

    val existingResults = listOf("alpha 1", "bravo 2", "bravo 5").map { itemText ->
      val item = SearchEverywhereItemMock(itemText)
      val itemId = runBlocking { session.saveItem(item) }
      val providerId = providers.first { itemText.startsWith(it.resultPrefix) }.id
      SearchEverywhereItemDataMock(itemId, providerId, weight = 0, ActionItemPresentation(text = itemText))
    }

    runBlocking {
      val searchJob = launch {
        println("Search will start")
        SearchEverywhereFrontendDispatcher().getItems(SearchEverywhereTextSearchParams("item", session), providers, providersWithLimits, existingResults).collect {
          println(it.presentation.text)
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
}