package com.jetbrains.fleet.rpc.plugin.ir.util

class Cache {
  private val cache = mutableMapOf<Any, Any?>()

  fun <T> remember(block: () -> T): T = cache.getOrPut(block) { block() } as T
}