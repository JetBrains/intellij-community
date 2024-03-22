// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.compatibility

import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.openapi.actionSystem.DataProvider

/**
 * Fast extension data without selection (allows to override cut/copy/paste providers)
 *
 * TODO consider a new extension for that OR new API for cut/copy/paste
 */
fun extensionData(dataId: String): Any? {
  return extensionData(dataId, emptyDataProvider)
}

private val emptyDataProvider = DataProvider { null }

fun extensionData(dataId: String, provider: DataProvider): Any? {
  for (modelExtension in NavBarModelExtension.EP_NAME.extensionList) {
    val data = modelExtension.getData(dataId, provider)
    if (data != null) return data
  }
  return provider.getData(dataId)
}
