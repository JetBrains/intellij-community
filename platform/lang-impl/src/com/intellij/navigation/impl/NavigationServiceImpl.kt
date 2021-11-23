// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation.impl

import com.intellij.navigation.NavigationRequest
import com.intellij.navigation.NavigationService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable

internal class NavigationServiceImpl : NavigationService {

  override fun sourceNavigationRequest(file: VirtualFile, offset: Int): NavigationRequest? {
    return SourceNavigationRequest(file, offset)
  }

  override fun rawNavigationRequest(navigatable: Navigatable): NavigationRequest? {
    return RawNavigationRequest(navigatable)
  }
}
