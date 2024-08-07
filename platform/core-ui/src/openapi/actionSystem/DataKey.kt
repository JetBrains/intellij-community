// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem

import com.intellij.openapi.util.ValueKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Type-safe named key.
 *
 * Mainly used via [AnActionEvent.getData] calls and [DataProvider.getData] implementations.
 *
 * Corresponding data for given `name` is provided by [DataProvider] implementations.
 * Globally available data can be provided via [GetDataRule] extension point.
 *
 * @see CommonDataKeys
 * @see PlatformDataKeys
 * @see LangDataKeys
 */
@Suppress("UNCHECKED_CAST")
class DataKey<T> private constructor(override val name: String) : ValueKey<T> {
  /**
   * For short notation, use `MY_KEY.is(dataId)` instead of `MY_KEY.getName().equals(dataId)`.
   *
   * @param dataId key name
   * @return `true` if name of DataKey equals to `dataId`, `false` otherwise
   */
  fun `is`(dataId: String?): Boolean = name == dataId

  fun getData(dataContext: DataContext): T? = dataContext.getData(this)

  fun getData(dataProvider: DataProvider): T? = dataProvider.getData(name) as T?

  companion object {
    private val ourDataKeyIndex: ConcurrentMap<String, DataKey<*>> = ConcurrentHashMap()

    @JvmStatic
    fun <T> create(name: @NonNls String): DataKey<T> {
      return ourDataKeyIndex.computeIfAbsent(name) { name: String -> DataKey<Any>(name) } as DataKey<T>
    }

    @JvmStatic
    @ApiStatus.Internal
    fun allKeys(): Array<DataKey<*>> = ourDataKeyIndex.values.toTypedArray()

    @JvmStatic
    @ApiStatus.Internal
    fun allKeysCount(): Int = ourDataKeyIndex.size
  }
}
