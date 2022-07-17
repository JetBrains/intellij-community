// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation.impl

import com.intellij.navigation.NavigationRequest
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SourceNavigationRequest internal constructor(val file: VirtualFile, val offset: Int) : NavigationRequest

@Internal
class RawNavigationRequest internal constructor(val navigatable: Navigatable) : NavigationRequest
