// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.DeferredIcon
import org.jetbrains.icons.Icon
import org.jetbrains.icons.IconIdentifier
import org.jetbrains.icons.impl.DefaultDeferredIcon
import org.jetbrains.icons.impl.DeferredIconResolver
import org.jetbrains.icons.impl.DeferredIconResolverService
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
@Service(Service.Level.APP)
class IntelliJDeferredIconResolverService(scope: CoroutineScope): DeferredIconResolverService(scope) {
  override fun scheduleEvaluation(icon: DeferredIcon) {
    if (!PowerSaveMode.isEnabled()) {
      super.scheduleEvaluation(icon)
    }
  }
}