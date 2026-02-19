// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * @param offsetMarker desired caret position, or `null` to keep the position unchanged
 * @param elementRangeMarker marker of a range, where the existing caret should remain unchanged
 * if [com.intellij.platform.ide.navigation.NavigationOptions.preserveCaret] is set,
 * or `null` to change the caret position according to [offsetMarker]
 */
@Internal
open class SourceNavigationRequest internal constructor(
  val file: VirtualFile,
  val offsetMarker: RangeMarker?,
  val elementRangeMarker: RangeMarker?,
) : NavigationRequest

@Internal
class SharedSourceNavigationRequest internal constructor(
  file: VirtualFile,
  val context: CodeInsightContext,
  offsetMarker: RangeMarker?,
  elementRangeMarker: RangeMarker?,
) : SourceNavigationRequest(file, offsetMarker, elementRangeMarker)

@Internal
class DirectoryNavigationRequest internal constructor(
  val directory: PsiDirectory,
) : NavigationRequest

@Internal
class RawNavigationRequest internal constructor(
  val navigatable: Navigatable,
  val canNavigateToSource: Boolean,
) : NavigationRequest
