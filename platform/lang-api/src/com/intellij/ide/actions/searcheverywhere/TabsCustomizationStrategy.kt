// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
interface TabsCustomizationStrategy {
  companion object {
    @JvmStatic
    fun getInstance(): TabsCustomizationStrategy {
      return ApplicationManager.getApplication().getService(TabsCustomizationStrategy::class.java)
    }
  }

  fun getSeparateTabContributors(contributors: List<SearchEverywhereContributor<*>>) : List<SearchEverywhereContributor<*>>
}