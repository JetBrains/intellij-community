// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.util.ui.StatusText
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SearchEverywhereEmptyTextProvider {
  fun updateEmptyStatus(statusText: StatusText, rebuild: () -> Unit)
}