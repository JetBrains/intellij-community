// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import kotlin.coroutines.coroutineContext

interface ResolvableApiEntity<T: RemoteApi<*>> : SharedEntity {
  fun resolve(transactor: Transactor, remoteApiDescriptor: RemoteApiDescriptor<T>): T
}

suspend inline fun <reified T: RemoteApi<*>> ResolvableApiEntity<T>.proxy(descriptor: RemoteApiDescriptor<T>): T =
  resolve(coroutineContext.transactor, descriptor)