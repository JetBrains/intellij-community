// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.distribution

import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath

class LocalDistributionInfo(path: String) : AbstractDistributionInfo() {
  var path = getCanonicalPath(path)
  var uiPath: String
    get() = getPresentablePath(path)
    set(value) {
      path = getCanonicalPath(value)
    }

  override val name: String by ::uiPath
  override val description: String? = null
}