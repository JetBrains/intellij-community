// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.internal.ml.DecisionFunction

class SearchEverywhereActionsRankingModelProvider : SearchEverywhereMLRankingModelProvider {
  override val model: DecisionFunction
    get() = TODO("Not yet implemented")
  override val displayNameInSettings: String
    get() = IdeBundle.message("searcheverywhere.ml.display.name.in.settings")

  override fun isContributorSupported(contributor: SearchEverywhereContributor<*>): Boolean {
    return contributor is ActionSearchEverywhereContributor
  }
}