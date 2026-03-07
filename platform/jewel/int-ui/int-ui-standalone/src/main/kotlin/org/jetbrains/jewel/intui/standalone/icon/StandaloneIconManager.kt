// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.icon

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.icons.Icon
import org.jetbrains.icons.IconIdentifier
import org.jetbrains.icons.design.IconDesigner
import org.jetbrains.icons.impl.DefaultIconManager
import org.jetbrains.icons.impl.DeferredIconResolverService

internal class StandaloneIconManager(scope: CoroutineScope) : DefaultIconManager() {
    override val resolverService: DeferredIconResolverService = DeferredIconResolverService(scope)

    override suspend fun sendDeferredNotifications(id: IconIdentifier, result: Icon) {
        // Do nothing
    }

    override fun markDeferredIconUnused(id: IconIdentifier) {
        resolverService.cleanIcon(id)
    }

    override fun icon(designer: IconDesigner.() -> Unit): Icon {
        val iconDesigner = StandaloneIconDesigner()
        iconDesigner.designer()
        return iconDesigner.build()
    }
}

internal class StandaloneDeferredIconResolverService(scope: CoroutineScope) : DeferredIconResolverService(scope)
