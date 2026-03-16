// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

@OptIn(ObsoleteWorkersApi::class)
@Actual
fun newSingleThreadCoroutineDispatcherNative(
  name: String,
  priority: DispatcherPriority
): HighPriorityCoroutineDispatcherResource =
  object : HighPriorityCoroutineDispatcherResource {
    private val worker = Worker.start(name = name)
    private val dispatcher: CoroutineDispatcher = WorkerDispatcher(worker)

    override suspend fun <U> use(body: suspend CoroutineScope.(CoroutineContext) -> U): U {
      return coroutineScope {
        try {
          body(dispatcher)
        } finally {
          // await termination, TODO: do we want to wait?
          worker.requestTermination().result
        }
      }
    }
  }

@OptIn(ObsoleteWorkersApi::class)
class WorkerDispatcher(private val worker: Worker) : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) {
    worker.execute(TransferMode.SAFE, { block }) { it.run() }
  }
}
