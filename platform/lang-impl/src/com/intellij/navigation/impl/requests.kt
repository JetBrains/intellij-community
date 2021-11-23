// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation.impl

import com.intellij.navigation.NavigationRequest
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable

internal class SourceNavigationRequest(val file: VirtualFile, val offset: Int) : NavigationRequest

internal class RawNavigationRequest(val navigatable: Navigatable) : NavigationRequest
