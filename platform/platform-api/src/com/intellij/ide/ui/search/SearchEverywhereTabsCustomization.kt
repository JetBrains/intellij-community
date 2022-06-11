// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
interface SearchEverywhereTabsCustomization {

  companion object {
    @JvmStatic fun getInstance():SearchEverywhereTabsCustomization =
      ApplicationManager.getApplication().getService(SearchEverywhereTabsCustomization::class.java)
  }

  fun getContributorsWithTab(): List<String>

}