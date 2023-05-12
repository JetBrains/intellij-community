// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation.impl

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SourceNavigationRequest internal constructor(
  val file: VirtualFile,
  val offsetMarker: RangeMarker?,
  val elementRangeMarker: RangeMarker?,
) : NavigationRequest

@Internal
class DirectoryNavigationRequest internal constructor(
  val directory: PsiDirectory,
) : NavigationRequest

@Internal
class RawNavigationRequest internal constructor(
  val navigatable: Navigatable,
  val canNavigateToSource: Boolean,
) : NavigationRequest
