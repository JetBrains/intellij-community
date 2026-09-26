// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider

internal interface NewFileViewProviderFactory {
  fun createNewFileViewProvider(file: VirtualFile, context: CodeInsightContext): FileViewProvider

  /**
   * Used by validity reanimation. Suppresses the irrelevant-context assertion enforced in
   * [com.intellij.psi.impl.file.impl.FileManagerImpl.createFileViewProvider] because the recreated
   * provider is transient and used only for equivalence comparison; it is not installed as PSI.
   */
  fun createNewFileViewProviderForValidityCheck(file: VirtualFile, context: CodeInsightContext): FileViewProvider
}