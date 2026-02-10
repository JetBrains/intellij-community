// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons

import kotlinx.coroutines.flow.Flow

@ExperimentalIconsApi
interface DynamicIcon: Icon {
  fun getCurrentIcon(): Icon
  suspend fun swap(icon: Icon)
  fun getFlow(): Flow<Icon>
}
