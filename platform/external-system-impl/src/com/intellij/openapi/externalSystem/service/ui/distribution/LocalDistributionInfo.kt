// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.distribution

import com.intellij.openapi.externalSystem.service.ui.getModelPath
import com.intellij.openapi.externalSystem.service.ui.getUiPath

class LocalDistributionInfo(path: String) : AbstractDistributionInfo() {
  var path = getModelPath(path)
  var uiPath: String
    get() = getUiPath(path)
    set(value) {
      path = getModelPath(value)
    }

  override val name: String by ::uiPath
  override val description: String? = null
}