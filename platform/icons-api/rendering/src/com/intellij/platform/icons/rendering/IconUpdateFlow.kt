// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.rendering

import com.intellij.platform.icons.Icon
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

typealias IconUpdateFlow = Flow<Int>

@ApiStatus.Internal
interface MutableIconUpdateFlow : IconUpdateFlow {
    fun triggerUpdate()

    fun triggerDelayedUpdate(delay: Long)

    fun collectDynamic(flow: Flow<Icon>, handler: (Icon) -> Unit) {}
}
