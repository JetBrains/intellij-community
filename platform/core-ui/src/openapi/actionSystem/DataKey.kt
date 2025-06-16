// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Type-safe named key to be used with [DataContext] API.
 *
 * UI components provide data for a key using [UiDataProvider] API.
 * Data can also be provided externally via [UiDataRule] extension point.
 *
 * @see CommonDataKeys
 * @see PlatformDataKeys
 * @see LangDataKeys
 */
@Suppress("UNCHECKED_CAST")
class DataKey<T> private constructor(val name: String) {
  /**
   * For short, use `KEY.is(dataId)` instead of `KEY.name.equals(dataId)`.
   */
  fun `is`(dataId: String?): Boolean = name == dataId

  fun getData(dataContext: DataContext): T? = dataContext.getData(this)

  @Deprecated("DataProvider must not be queried explicitly. " +
              "Use DataContext or a regular getter instead")
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
