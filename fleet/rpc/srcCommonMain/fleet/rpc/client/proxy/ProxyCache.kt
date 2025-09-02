// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client.proxy

import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.core.InstanceId
import fleet.util.UID
import fleet.multiplatform.shims.ConcurrentHashMap

interface ProxyCache<K : Any> {
  fun <T : Any> proxy(remoteApiDescriptor: RemoteApiDescriptor<*>, key: K, proxy: () -> T): T
}

// TODO LRU?
fun <K : Any> proxyCache(): ProxyCache<K> {
  data class ProxyCacheValue(val proxy: Any,
                             val remoteApiDescriptor: RemoteApiDescriptor<*>)

  val cache = ConcurrentHashMap<K, ProxyCacheValue>()
  return object : ProxyCache<K> {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> proxy(remoteApiDescriptor: RemoteApiDescriptor<*>, key: K, proxy: () -> T): T =
      cache.compute(key) { _, value ->
        when {
          value == null || value.remoteApiDescriptor != remoteApiDescriptor -> {
            ProxyCacheValue(remoteApiDescriptor = remoteApiDescriptor,
                            proxy = proxy())
          }
          else -> value
        }
      }!!.proxy as T
  }
}


fun ServiceProxy.caching(): ServiceProxy =
  let { delegate ->
    val proxyCache = proxyCache<Pair<UID, InstanceId>>()
    object : ServiceProxy {
      override fun <A : RemoteApi<*>> proxy(
        remoteApiDescriptor: RemoteApiDescriptor<A>,
        route: UID,
        instanceId: InstanceId
      ): A =
        proxyCache.proxy(remoteApiDescriptor, route to instanceId) {
          delegate.proxy(remoteApiDescriptor, route, instanceId)
        }
    }
  }
