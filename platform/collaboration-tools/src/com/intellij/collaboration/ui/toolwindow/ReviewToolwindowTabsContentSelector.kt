// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.toolwindow

import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface ReviewToolwindowTabsContentSelector<T : ReviewTab> {
  // TODO: it is better to move this logic to ReviewTabsController but I don't want to provide Content there,
  //  so later it would be better to provide ViewModels instead of the Content and such API can be exposed in ReviewTabsController
  suspend fun selectTab(reviewTab: T): Content?
}