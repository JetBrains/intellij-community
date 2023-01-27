// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Key

object ReviewToolwindowDataKeys {
  @JvmStatic
  val REVIEW_TABS_CONTROLLER = DataKey.create<ReviewTabsController<*>>("com.intellij.collaboration.toolwindow.review.tabs.controller")
}

object ReviewToolwindowUserDataKeys {
  @JvmStatic
  val REVIEW_TAB: Key<ReviewTab> = Key.create("com.intellij.collaboration.toolwindow.review.tab")
}