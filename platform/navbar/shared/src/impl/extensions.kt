// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.impl

import com.intellij.ide.impl.DataValidators
import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.openapi.actionSystem.CompositeDataProvider
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys

fun extensionData(dataId: String, provider: DataProvider): Any? {
  if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.`is`(dataId)) {
    val bgtProviders = NavBarModelExtension.EP_NAME.extensionList.mapNotNull {
      it.getData(dataId, provider) as? DataProvider
    }
    return CompositeDataProvider.compose(bgtProviders)
  }
  for (modelExtension in NavBarModelExtension.EP_NAME.extensionList) {
    val data = modelExtension.getData(dataId, provider)
    if (data != null) {
      return DataValidators.validOrNull(data, dataId, modelExtension)
    }
  }
  return provider.getData(dataId)
}
