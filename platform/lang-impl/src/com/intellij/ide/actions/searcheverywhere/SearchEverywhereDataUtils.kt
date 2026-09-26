// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun <Item : Any> SearchEverywhereContributor<Item>.addDataForItem(element: Item, sink: DataSink) {
  val dataProviders = getDataProviders();

  if (dataProviders.isEmpty()) {
    sink[PlatformCoreDataKeys.BGT_DATA_PROVIDER] = DataProvider { dataId -> getDataForItem(element, dataId) }
  }
  else {
    dataProviders.forEach {
      it.accept(element, sink)
    }
  }
}