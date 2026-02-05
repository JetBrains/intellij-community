// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalIconsApi::class)

package org.jetbrains.jewel.intui.standalone.icon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.KSerializer
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.Icon
import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.MutableIconUpdateFlow
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.impl.rendering.CoroutineBasedMutableIconUpdateFlow
import org.jetbrains.icons.impl.rendering.DefaultIconRendererManager

@OptIn(ExperimentalIconsApi::class)
internal class StandaloneIconRendererManager : DefaultIconRendererManager() {
    override fun createUpdateFlow(scope: CoroutineScope?, updateCallback: (Int) -> Unit): MutableIconUpdateFlow {
        if (scope == null) return EmptyMutableIconUpdateFlow()
        return CoroutineBasedMutableIconUpdateFlow(scope, updateCallback)
    }

    override fun createRenderingContext(
        updateFlow: MutableIconUpdateFlow,
        defaultImageModifiers: ImageModifiers?,
    ): RenderingContext {
        return RenderingContext(updateFlow, defaultImageModifiers)
    }
}

private class EmptyMutableIconUpdateFlow : MutableIconUpdateFlow {
    override fun triggerUpdate() {
        // Do nothing
    }

    override fun triggerDelayedUpdate(delay: Long) {
        // Do nothing
    }

    override suspend fun collect(collector: FlowCollector<Int>) {
        // Do nothing
    }

    override fun collectDynamic(flow: Flow<Icon>, handler: (Icon) -> Unit) {
        // Do nothing
    }
}
