// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.jewel.intui.standalone.icon

import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.impl.rendering.CoroutineBasedMutableIconUpdateFlow
import com.intellij.platform.icons.impl.rendering.DefaultIconRendererManager
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.rendering.ThemeContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.jewel.ui.icon.ComposeImageResourceProvider

internal class StandaloneIconRendererManager : DefaultIconRendererManager() {
    private val imageProvider = ComposeImageResourceProvider()

    override fun createUpdateFlow(scope: CoroutineScope?, onUpdate: (suspend (Int) -> Unit)?): MutableIconUpdateFlow {
        if (scope == null) return EmptyMutableIconUpdateFlow()
        return CoroutineBasedMutableIconUpdateFlow(scope, onUpdate)
    }

    override fun createRenderingContext(
        updateFlow: MutableIconUpdateFlow,
        defaultImageModifiers: ImageModifiers?,
    ): RenderingContext {
        val knownModifiers = defaultImageModifiers as? DefaultImageModifiers
        return DefaultRenderingContext(updateFlow, knownModifiers, ThemeContext.None, imageProvider)
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
