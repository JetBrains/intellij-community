package com.intellij.database.util

import com.intellij.openapi.util.registry.Registry.Companion.intValue

class LobInfoHelper {
  companion object {
    @JvmField
    val MAX_ARRAY_SIZE: Int = intValue("database.arrays.maxSize", 1000)
  }
}