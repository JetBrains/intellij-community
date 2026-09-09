// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.icon

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconIdentifier
import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.impl.DefaultIconManager
import com.intellij.platform.icons.impl.DeferredIconResolverService
import kotlinx.coroutines.CoroutineScope

internal class StandaloneIconManager(scope: CoroutineScope) : DefaultIconManager() {
    override val resolverService: DeferredIconResolverService = DeferredIconResolverService(scope)

    override suspend fun sendDeferredNotifications(id: IconIdentifier, result: IconDescriptor) {
        // Do nothing
    }

    override fun markDeferredIconUnused(id: IconIdentifier) {
        resolverService.cleanIcon(id)
    }

    override fun iconDescriptor(designer: IconDesigner.() -> Unit): IconDescriptor {
        val iconDesigner = StandaloneIconDesigner()
        iconDesigner.designer()
        return iconDesigner.build()
    }
}
