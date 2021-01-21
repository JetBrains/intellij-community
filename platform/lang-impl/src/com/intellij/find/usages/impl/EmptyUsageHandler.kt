// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.impl

import com.intellij.find.FindBundle
import com.intellij.find.usages.api.NonConfigurableUsageHandler
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageOptions
import com.intellij.util.EmptyQuery
import com.intellij.util.Query

internal class EmptyUsageHandler(private val targetName: String) : NonConfigurableUsageHandler() {

  override fun getSearchString(options: UsageOptions): String = FindBundle.message("usages.search.title.default", targetName)

  override fun buildSearchQuery(options: UsageOptions): Query<out Usage> = EmptyQuery.getEmptyQuery()
}
