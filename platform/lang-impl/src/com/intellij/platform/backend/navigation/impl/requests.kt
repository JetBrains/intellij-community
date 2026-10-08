// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * @param offsetMarker desired caret position, or `null` to keep the position unchanged
 * @param elementRangeMarker marker of a range, where the existing caret should remain unchanged
 * if [com.intellij.platform.ide.navigation.NavigationOptions.preserveCaret] is set,
 * or `null` to change the caret position according to [offsetMarker]
 * @param initialOffset the supplied offset, before the marker factory clamps it to the estimated document length
 * @param initialFileStamp the file modification stamp when the request was created
 * @param initialDocumentStamp the document modification stamp when the request was created, or `null` if no document was loaded
 * @param lazyDecompilerElement the element whose offset is read after its document loads
 * @param useCurrentWindow matches [com.intellij.openapi.fileEditor.OpenFileDescriptor.setUseCurrentWindow]
 * @param usePreviewTab matches [com.intellij.openapi.fileEditor.OpenFileDescriptor.setUsePreviewTab]
 */
@Internal
open class SourceNavigationRequest internal constructor(
  val file: VirtualFile,
  val offsetMarker: RangeMarker?,
  val elementRangeMarker: RangeMarker?,
  internal val initialOffset: Int?,
  internal val initialFileStamp: Long,
  internal val initialDocumentStamp: Long?,
  internal val lazyDecompilerElement: SmartPsiElementPointer<PsiElement>? = null,
  internal val useCurrentWindow: Boolean = false,
  internal val usePreviewTab: Boolean = false,
) : NavigationRequest

@Internal
class SharedSourceNavigationRequest internal constructor(
  file: VirtualFile,
  val context: CodeInsightContext,
  offsetMarker: RangeMarker?,
  elementRangeMarker: RangeMarker?,
  initialOffset: Int?,
  initialFileStamp: Long,
  initialDocumentStamp: Long?,
  lazyDecompilerElement: SmartPsiElementPointer<PsiElement>? = null,
  useCurrentWindow: Boolean = false,
  usePreviewTab: Boolean = false,
) : SourceNavigationRequest(
  file,
  offsetMarker,
  elementRangeMarker,
  initialOffset,
  initialFileStamp,
  initialDocumentStamp,
  lazyDecompilerElement,
  useCurrentWindow,
  usePreviewTab,
)

@get:Internal
val SourceNavigationRequest.contextOrAny: CodeInsightContext
  get() = if (this is SharedSourceNavigationRequest) context else anyContext()

@Internal
class DirectoryNavigationRequest internal constructor(
  val directory: PsiDirectory,
) : NavigationRequest

@Internal
class RawNavigationRequest internal constructor(
  val navigatable: Navigatable,
  val canNavigateToSource: Boolean,
) : NavigationRequest
