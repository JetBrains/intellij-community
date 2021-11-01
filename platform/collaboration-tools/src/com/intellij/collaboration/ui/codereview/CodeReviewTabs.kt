// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview

import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.tabs.TabInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

object CodeReviewTabs {
  fun CoroutineScope.bindTabText(tab: TabInfo, text: Supplier<@Nls String>, countFlow: Flow<Int?>): Job =
    countFlow
      .onEach { count ->
        tab.clearText(false).append(text.get(), REGULAR_ATTRIBUTES)

        count?.let { tab.append("  $it", GRAYED_ATTRIBUTES) }
      }
      .launchIn(this)
}
