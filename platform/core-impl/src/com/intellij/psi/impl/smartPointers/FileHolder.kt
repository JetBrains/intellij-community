// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FileHolder private constructor(
  internal val context: CodeInsightContext?,
  internal val virtualFile: VirtualFile,
) {

  internal companion object {
    private val cacheKey = Key.create<FileHolder>("ContextHolderKey")

    @JvmStatic
    fun create(context: CodeInsightContext?, file: VirtualFile): FileHolder =
      FileHolder(context, file)

    @JvmStatic
    @JvmOverloads
    fun createInterned(file: PsiFile, tracker: SmartPointerTracker? = null): FileHolder = when {
      tracker != null -> tracker.createFileHolderInterned(file.viewProvider.virtualFile, file.codeInsightContext)
      else -> file.viewProvider.getOrCreateUserData(cacheKey) { FileHolder(file.codeInsightContext, file.viewProvider.virtualFile) }
    }

    fun createInterned(file: PsiFile, manager: SmartPointerManagerEx?): FileHolder =
      createInterned(file, manager?.getOrCreateTracker(file.viewProvider.virtualFile))

    fun createInterned(virtualFile: VirtualFile, context: CodeInsightContext?, tracker: SmartPointerTracker?): FileHolder =
      when (tracker != null) {
        true -> tracker.createFileHolderInterned(virtualFile, context)
        false -> FileHolder(context, virtualFile)
      }
  }
}
