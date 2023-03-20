// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus

object ReviewToolwindowDataKeys {
  @JvmStatic
  val REVIEW_TABS_CONTROLLER = DataKey.create<ReviewTabsController<*>>("com.intellij.collaboration.toolwindow.review.tabs.controller")

  @JvmStatic
  val REVIEW_PROJECT_CONTEXT = DataKey.create<ReviewToolwindowProjectContext>("com.intellij.collaboration.toolwindow.review.project.context")

  @JvmStatic
  val REVIEW_TOOLWINDOW_VM = DataKey.create<ReviewToolwindowViewModel<*>>("com.intellij.collaboration.toolwindow.review.toolwindow.vm")

  @ApiStatus.Internal
  @JvmStatic
  val REVIEW_TABS_CONTENT_SELECTOR = DataKey.create<ReviewToolwindowTabsContentSelector<*>>("com.intellij.collaboration.toolwindow.review.tabs.content.selector")
}