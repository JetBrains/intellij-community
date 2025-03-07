// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

@Actual("newSingleThreadCoroutineDispatcher")
fun newSingleThreadCoroutineDispatcherJvm(
  name: String,
  priority: DispatcherPriority
): HighPriorityCoroutineDispatcherResource =
  object : HighPriorityCoroutineDispatcherResource {
    override suspend fun <U> use(body: suspend (CoroutineContext) -> U): U {
      val changesThread = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, name).apply {
          this.isDaemon = true
          this.priority = when (priority) {
            DispatcherPriority.HIGH -> Thread.MAX_PRIORITY
            DispatcherPriority.NORMAL -> Thread.NORM_PRIORITY
            DispatcherPriority.LOW -> Thread.MIN_PRIORITY
          }
        }
      }

      return try {
        body(changesThread.asCoroutineDispatcher())
      }
      finally {
        changesThread.shutdown()
      }
    }
  }