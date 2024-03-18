// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.resume

fun <T : Any> ExtensionPointName<T>.lazyDumbAwareExtensions(project: Project): Sequence<T> {
  return if (DumbService.getInstance(project).isDumb) lazySequence().filter { DumbService.isDumbAware(it) } else lazySequence()
}

/**
 * Suspends until a project becomes smart.
 * NB: One should not rely upon "smartness" after this function resumes, because the project may become dumb again.
 *
 * @see DumbService.waitForSmartMode
 */
@Internal
suspend fun Project.waitForSmartMode() {
  suspendCancellableCoroutine { continuation ->
    DumbService.getInstance(this).runWhenSmart(ContextAwareRunnable {
      continuation.resume(Unit)
    })
  }
}
