// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere

import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class SearchEverywhereListItem<I, P: SearchEverywhereItemPresentation>(
  val item: I,
  val presentation: P,
  val weight: Int,
  val dataContext: DataContext,
  val textDescription: String? = null,
) {}