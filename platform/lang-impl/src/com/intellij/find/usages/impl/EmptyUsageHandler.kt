// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.impl

import com.intellij.find.FindBundle
import com.intellij.find.usages.NonConfigurableUsageHandler
import com.intellij.find.usages.UsageOptions
import com.intellij.model.Symbol
import com.intellij.model.presentation.SymbolPresentationService
import com.intellij.usages.Usage
import com.intellij.util.EmptyQuery
import com.intellij.util.Query

internal class EmptyUsageHandler(private val symbol: Symbol) : NonConfigurableUsageHandler() {

  override fun getSearchString(options: UsageOptions): String {
    val shortNameString = SymbolPresentationService.getInstance().getSymbolPresentation(symbol).shortNameString
    return FindBundle.message("usages.search.title.default", shortNameString)
  }

  override fun buildSearchQuery(options: UsageOptions): Query<out Usage> = EmptyQuery.getEmptyQuery()
}
