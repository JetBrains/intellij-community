// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ObservableOptionEditor<O: Any> : OptionEditor<O> {
  val resultFlow: StateFlow<O?>
}