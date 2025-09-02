// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.ids

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.APP)
internal class BackendGlobalIdsManager {
  private val idsCounter = AtomicInteger()
  private val ids = ConcurrentHashMap<UID, Any>()

  @OptIn(AwaitCancellationAndInvoke::class)
  fun <TID : Id, Value : Any> putId(
    coroutineScope: CoroutineScope?,
    value: Value,
    idsFactory: (UID) -> TID,
  ): TID {
    val newId = idsCounter.incrementAndGet()
    ids.put(newId, value)
    coroutineScope?.awaitCancellationAndInvoke {
      ids.remove(newId)
    }
    return idsFactory(newId)
  }

  fun <TID : Id> removeId(id: TID) {
    ids.remove(id.uid)
  }

  @Suppress("UNCHECKED_CAST")
  fun <TID : Id, T> findById(id: TID): T? {
    return ids[id.uid] as T?
  }

  companion object {
    @JvmStatic
    fun getInstance(): BackendGlobalIdsManager = service()
  }
}