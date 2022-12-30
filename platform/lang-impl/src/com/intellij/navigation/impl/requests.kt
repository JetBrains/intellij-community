// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation.impl

import com.intellij.navigation.NavigationRequest
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SourceNavigationRequest internal constructor(val file: VirtualFile, val offset: Int) : NavigationRequest

@Internal
class DirectoryNavigationRequest internal constructor(val directory: PsiDirectory) : NavigationRequest

@Internal
class RawNavigationRequest internal constructor(val navigatable: Navigatable) : NavigationRequest
