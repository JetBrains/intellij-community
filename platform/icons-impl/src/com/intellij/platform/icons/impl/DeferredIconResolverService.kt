// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.DeferredIcon
import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.IconIdentifier
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class DeferredIconResolverService(protected val scope: CoroutineScope) {
    protected val iconReferenceQueue: ReferenceQueue<DefaultDeferredIcon> = ReferenceQueue<DefaultDeferredIcon>()
    protected val resolvers: ConcurrentHashMap<IconIdentifier, DeferredIconResolver> =
        ConcurrentHashMap<IconIdentifier, DeferredIconResolver>()

    init {
        scope.launch {
            while (true) {
                delay(5.seconds)
                cleanUnusedIcons()
            }
        }
    }

    open fun getOrCreateDeferredIcon(
        identifier: IconIdentifier,
        placeholder: Icon?,
        resolverBuilder: (IconIdentifier, WeakReference<DefaultDeferredIcon>) -> DeferredIconResolver,
    ): Icon {
        val resolver =
            resolvers.getOrPut(identifier) {
                val icon = DefaultDeferredIcon(identifier, placeholder)
                resolverBuilder(icon.id, IdentifiedDeferredIconWeakReference(icon, iconReferenceQueue))
            }
        return resolver?.deferredIcon?.get() ?: DefaultDeferredIcon(identifier, placeholder)
    }

    open fun register(
        icon: DefaultDeferredIcon,
        resolverBuilder: (IconIdentifier, WeakReference<DefaultDeferredIcon>) -> DeferredIconResolver,
    ): DefaultDeferredIcon =
        resolvers
            .getOrPut(icon.id) {
                resolverBuilder(icon.id, IdentifiedDeferredIconWeakReference(icon, iconReferenceQueue))
            }
            ?.deferredIcon
            ?.get() ?: icon

    open fun scheduleEvaluation(icon: DeferredIcon) {
        scope.launch { forceEvaluation(icon) }
    }

    open suspend fun forceEvaluation(icon: DeferredIcon): Icon {
        val resolver = resolvers[icon.id] ?: error("Cannot find resolver for icon: $icon")
        return resolver.resolve()
    }

    open fun cleanIcon(id: IconIdentifier) {
        resolvers.remove(id)
    }

    protected open fun cleanUnusedIcons() {
        while (true) {
            val reference = iconReferenceQueue.poll() ?: break
            val id = (reference as IdentifiedDeferredIconWeakReference).id
            DefaultIconManager.getDefaultManagerInstance().markDeferredIconUnused(id)
        }
    }

    protected open class IdentifiedDeferredIconWeakReference(
        instance: DefaultDeferredIcon,
        queue: ReferenceQueue<DefaultDeferredIcon>,
    ) : WeakReference<DefaultDeferredIcon>(instance, queue) {
        val id: IconIdentifier = instance.id
    }
}
