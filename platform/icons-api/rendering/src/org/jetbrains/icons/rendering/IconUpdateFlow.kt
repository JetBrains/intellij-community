// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering

import kotlinx.coroutines.flow.Flow
import org.jetbrains.icons.Icon
import org.jetbrains.icons.InternalIconsApi

@InternalIconsApi
typealias IconUpdateFlow = Flow<Int>

@InternalIconsApi
interface MutableIconUpdateFlow: IconUpdateFlow {
  fun triggerUpdate()
  fun triggerDelayedUpdate(delay: Long)
  fun collectDynamic(flow: Flow<Icon>, handler: (Icon) -> Unit) {}
}