// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.DeferredIconDescriptor
import com.intellij.platform.icons.IconDescriptor
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
    protected val iconReferenceQueue: ReferenceQueue<DefaultDeferredIconDescriptor> = ReferenceQueue<DefaultDeferredIconDescriptor>()
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
      placeholder: IconDescriptor?,
      resolverBuilder: (IconIdentifier, WeakReference<DefaultDeferredIconDescriptor>) -> DeferredIconResolver,
    ): IconDescriptor {
        val resolver =
            resolvers.getOrPut(identifier) {
                val icon = DefaultDeferredIconDescriptor(identifier, placeholder)
                resolverBuilder(icon.id, IdentifiedDeferredIconWeakReference(icon, iconReferenceQueue))
            }
        return resolver?.deferredIcon?.get() ?: DefaultDeferredIconDescriptor(identifier, placeholder)
    }

    open fun register(
      icon: DefaultDeferredIconDescriptor,
      resolverBuilder: (IconIdentifier, WeakReference<DefaultDeferredIconDescriptor>) -> DeferredIconResolver,
    ): DefaultDeferredIconDescriptor =
        resolvers
            .getOrPut(icon.id) {
                resolverBuilder(icon.id, IdentifiedDeferredIconWeakReference(icon, iconReferenceQueue))
            }
            ?.deferredIcon
            ?.get() ?: icon

    open fun scheduleEvaluation(icon: DeferredIconDescriptor) {
        scope.launch { forceEvaluation(icon) }
    }

    open suspend fun forceEvaluation(icon: DeferredIconDescriptor): IconDescriptor {
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
      instance: DefaultDeferredIconDescriptor,
      queue: ReferenceQueue<DefaultDeferredIconDescriptor>,
    ) : WeakReference<DefaultDeferredIconDescriptor>(instance, queue) {
        val id: IconIdentifier = instance.id
    }
}
