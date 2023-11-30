// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.openapi.actionSystem.DataKey

object ReviewToolwindowDataKeys {
  @JvmStatic
  val REVIEW_TOOLWINDOW_PROJECT_VM =
    DataKey.create<ReviewToolwindowProjectViewModel<*, *>>("com.intellij.collaboration.toolwindow.review.project.vm")

  @JvmStatic
  val REVIEW_TOOLWINDOW_VM = DataKey.create<ReviewToolwindowViewModel<*>>("com.intellij.collaboration.toolwindow.review.toolwindow.vm")
}