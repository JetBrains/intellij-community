// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation.impl

import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AsyncNavigatable : Navigatable {
  suspend fun navigateAsync(requestFocus: Boolean)
}
