// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.model

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.internal.ml.DecisionFunction
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Provides model to predict relevance of each element in Search Everywhere tab
 */
@ApiStatus.Internal
interface SearchEverywhereMLRankingModelProvider {
  val model: DecisionFunction

  val displayNameInSettings: @Nls(capitalization = Nls.Capitalization.Title) String

  val isEnabledByDefault: Boolean
    get() = false

  fun isContributorSupported(contributor: SearchEverywhereContributor<*>): Boolean
}