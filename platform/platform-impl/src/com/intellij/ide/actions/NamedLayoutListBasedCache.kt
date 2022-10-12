// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import com.intellij.util.containers.toArray

class NamedLayoutListBasedCache<T>(private val mapping: (String) -> T?) {

  var dependsOnActiveLayout: Boolean = false

  private var cachedData: CachedData<T>? = null

  fun getCachedOrUpdatedArray(emptyArray: Array<T>): Array<T> {
    val cachedData = this.cachedData
    val manager = ToolWindowDefaultLayoutManager.getInstance()
    val newLayoutNames = manager.getLayoutNames()
    val newActiveLayoutName = if (dependsOnActiveLayout) manager.activeLayoutName else null
    if (
      newLayoutNames == cachedData?.cachedLayoutNames &&
      newActiveLayoutName == cachedData.activeLayoutName
    ) {
      return cachedData.cachedArray
    }
    val newArray = newLayoutNames.asSequence()
      .sorted()
      .mapNotNull(mapping)
      .toList()
      .toArray(emptyArray)
    this.cachedData = CachedData(newActiveLayoutName, newLayoutNames, newArray)
    return newArray
  }

  private class CachedData<T>(
    val activeLayoutName: String?,
    val cachedLayoutNames: Set<String>,
    val cachedArray: Array<T>,
  )

}
