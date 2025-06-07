// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.toolWindow.ToolWindowDefaultLayoutManager

internal class NamedLayoutListBasedCache<T>(
  private val baseList: List<T>,
  private val insertionIndex: Int,
  private val mapping: (String) -> T?
) {

  private var cachedData: CachedData<T>? = null

  fun getCachedOrUpdatedArray(emptyArray: Array<T>): Array<T> {
    val cachedData = this.cachedData
    val manager = ToolWindowDefaultLayoutManager.getInstance()
    val newLayoutNames = manager.getLayoutNames()
    if (newLayoutNames == cachedData?.cachedLayoutNames) {
      return cachedData.cachedArray
    }
    val newElements = newLayoutNames.asSequence()
      .sorted()
      .mapNotNull(mapping)
      .toList()
    val newList = ArrayList(baseList)
    newList.addAll(insertionIndex, newElements)
    val newArray = newList.toArray(emptyArray)
    this.cachedData = CachedData(newLayoutNames, newArray)
    return newArray
  }

  private class CachedData<T>(
    val cachedLayoutNames: Set<String>,
    val cachedArray: Array<T>,
  )

}
