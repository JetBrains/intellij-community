// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.impl

import com.intellij.platform.util.coroutines.forEachConcurrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private object StartupClassPreloader

/**
 * Loads and links the UI classes that the project frame and the first editor need, so the EDT does not do it.
 * The list is recorded from a startup class trace.
 */
@ApiStatus.Internal
suspend fun preloadStartupUiClasses() {
  val loader = StartupClassPreloader::class.java.classLoader
  val names = StartupClassPreloader::class.java.getResourceAsStream("startup-ui-classes.txt")?.bufferedReader()?.use { reader ->
    reader.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
  } ?: return
  withContext(Dispatchers.Default) {
    names.forEachConcurrent(concurrency = 4) { name ->
      try {
        Class.forName(name, false, loader).declaredConstructors
      }
      catch (_: Throwable) {
        currentCoroutineContext().ensureActive()
      }
    }
  }
}
