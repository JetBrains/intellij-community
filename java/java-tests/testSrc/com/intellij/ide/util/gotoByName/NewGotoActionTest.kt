// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.searchEverywhere.SearchEverywhereItemPresentation
import com.intellij.searchEverywhere.SearchEverywhereViewItemsProvider
import com.intellij.searchEverywhere.core.DefaultViewItemsProvider
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
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

  private fun createProvider(): SearchEverywhereViewItemsProvider<GotoActionModel.MatchedValue, SearchEverywhereItemPresentation, ActionSearchParams> {
    val itemsProvider = ActionsItemsProvider(project, null, null)
    return DefaultViewItemsProvider<GotoActionModel.MatchedValue, SearchEverywhereItemPresentation, ActionSearchParams>(
      itemsProvider,
      ActionPresentationProvider,
      { SimpleDataContext.getProjectContext(project) }
    )
  }
}