// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
@file:JvmName("LightFileCodeInsightContexts")

package com.intellij.codeInsight.multiverse

import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

/**
 * Sets [context] on the light file of [fileViewProvider], see [CodeInsightContextManagerImpl.setCodeInsightContextOfLightFile].
 * It does nothing when the file of [fileViewProvider] is not a [LightVirtualFile].
 */
@RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
fun setCodeInsightContextOfLightFile(fileViewProvider: FileViewProvider, context: CodeInsightContext) {
  val file = fileViewProvider.virtualFile as? LightVirtualFile ?: return
  CodeInsightContextManagerImpl.getInstanceImpl(fileViewProvider.manager.project).setCodeInsightContextOfLightFile(file, context)
}

/** Sets [context] on the light file of [psiFile], as the overload for a [FileViewProvider] does. */
@RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
fun setCodeInsightContextOfLightFile(psiFile: PsiFile, context: CodeInsightContext) {
  setCodeInsightContextOfLightFile(psiFile.viewProvider, context)
}
