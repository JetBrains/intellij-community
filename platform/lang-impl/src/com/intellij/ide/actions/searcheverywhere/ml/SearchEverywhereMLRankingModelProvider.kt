// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.completion.DecoratingItemsPolicy
import com.intellij.internal.ml.completion.DecoratingItemsPolicy.Companion.DISABLED
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Provides model to predict relevance of each element in the completion popup
 */
@ApiStatus.Internal
interface SearchEverywhereMLRankingModelProvider {
  val model: DecisionFunction
  val displayNameInSettings: @Nls(capitalization = Nls.Capitalization.Title) String
  val id: @NonNls String
    get() = displayNameInSettings
  val isEnabledByDefault: Boolean
    get() = false
  val decoratingPolicy: DecoratingItemsPolicy?
    get() = DISABLED
  
  fun isContributorSupported(contributor: SearchEverywhereContributor<*>): Boolean
}