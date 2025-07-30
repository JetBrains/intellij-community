// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.resume

fun <T : Any> ExtensionPointName<T>.lazyDumbAwareExtensions(project: Project): Sequence<T> {
  return lazySequence().filter { DumbService.getInstance(project).isUsableInCurrentContext(it) }
}

/**
 * Suspends until a project becomes smart.
 * NB: One should not rely upon "smartness" after this function resumes, because the project may become dumb again.
 *
 * In cases when your code needs waiting for the initial import, indexing, configuration and does not change the configuration,
 * consider using [Observation.awaitConfiguration].
 *
 * @see DumbService.waitForSmartMode
 */
@ApiStatus.Experimental
suspend fun Project.waitForSmartMode() {
  suspendCancellableCoroutine { continuation ->
    DumbService.getInstance(this).runWhenSmart(ContextAwareRunnable {
      continuation.resume(Unit)
    })
  }
}
