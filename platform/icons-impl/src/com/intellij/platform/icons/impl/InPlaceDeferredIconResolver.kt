// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.IconIdentifier
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InPlaceDeferredIconResolver(
    val service: DeferredIconResolverService,
    override val id: IconIdentifier,
    override val deferredIcon: WeakReference<DefaultDeferredIcon>,
    val evaluator: suspend () -> Icon,
) : DeferredIconResolver {
    var resolvedIcon: Icon? = null
    private val deferredValue = CompletableDeferred<Icon>()
    private val isPending = AtomicBoolean(false)

    override suspend fun resolve(): Icon {
        val resolved = resolvedIcon
        if (resolved != null) return resolved
        if (!isPending.getAndSet(true)) {
            val result = evaluator()
            deferredValue.complete(result)
            resolvedIcon = result
            deferredIcon.get()?.markDone(result)
            DefaultIconManager.getDefaultManagerInstance().sendDeferredNotifications(id, result)
            return result
        }
        return deferredValue.await()
    }
}
