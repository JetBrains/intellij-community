// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.searchEverywhere.frontend.SearchEverywhereFrontendDispatcher
import com.intellij.searchEverywhere.mocks.*
import com.intellij.searchEverywhere.shared.OptionItemPresentation
import com.intellij.searchEverywhere.shared.SearchEverywhereItemsProvider
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
      val params = SearchEverywhereParamsMock("it")

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

        val params = SearchEverywhereParamsMock("item")
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
      (searchEverywhereViewItemsProvider as SearchEverywhereItemsProvider).id to (index + 1) * 3
    }.toMap()

    val session = SearchEverywhereSessionMock()

    val existingResults = listOf("alpha 1", "bravo 2", "bravo 5").map { itemText ->
      val item = SearchEverywhereItemMock(itemText)
      val itemId = session.saveItem(item)
      val providerId = providers.first { itemText.startsWith(it.resultPrefix) }.id
      SearchEverywhereItemDataMock(itemId, providerId, weight = 0, OptionItemPresentation(text = itemText))
    }

    runBlocking {
      val searchJob = launch {
        println("Search will start")
        SearchEverywhereFrontendDispatcher().getItems(SearchEverywhereParamsMock("item"), providers, providersWithLimits, existingResults).collect {
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