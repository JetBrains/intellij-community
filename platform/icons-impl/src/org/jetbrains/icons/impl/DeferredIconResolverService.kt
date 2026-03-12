// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.DeferredIcon
import org.jetbrains.icons.Icon
import org.jetbrains.icons.IconIdentifier
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
open class DeferredIconResolverService(
  protected val scope: CoroutineScope
) {
  protected val iconReferenceQueue = ReferenceQueue<DefaultDeferredIcon>()
  protected val resolvers = ConcurrentHashMap<IconIdentifier, DeferredIconResolver>()

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
    resolverBuilder: (IconIdentifier, WeakReference<DefaultDeferredIcon>) -> DeferredIconResolver
  ): Icon {
    val resolver = resolvers.getOrPut(identifier) {
      val icon = DefaultDeferredIcon(identifier, placeholder)
      resolverBuilder(icon.id, IdentifiedDeferredIconWeakReference(icon, iconReferenceQueue))
    }
    return resolver?.deferredIcon?.get() ?: DefaultDeferredIcon(identifier, placeholder)
  }

  open fun register(
    icon: DefaultDeferredIcon,
    resolverBuilder: (IconIdentifier, WeakReference<DefaultDeferredIcon>) -> DeferredIconResolver
  ): DefaultDeferredIcon {
    return resolvers.getOrPut(icon.id) {
      resolverBuilder(icon.id, IdentifiedDeferredIconWeakReference(icon, iconReferenceQueue))
    }?.deferredIcon?.get() ?: icon
  }

  open fun scheduleEvaluation(icon: DeferredIcon) {
    scope.launch {
      forceEvaluation(icon)
    }
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
      val id = (reference as IdentifiedDeferredIconWeakReference).id ?: continue
      DefaultIconManager.getDefaultManagerInstance().markDeferredIconUnused(id)
    }
  }

  protected open class IdentifiedDeferredIconWeakReference(
    instance: DefaultDeferredIcon,
    queue: ReferenceQueue<DefaultDeferredIcon>,
  ): WeakReference<DefaultDeferredIcon>(instance, queue) {
    val id = instance.id
  }
}