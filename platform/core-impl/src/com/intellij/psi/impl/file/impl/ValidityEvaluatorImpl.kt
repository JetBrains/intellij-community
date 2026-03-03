// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.codeInsight.multiverse.defaultContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.StackOverflowPreventedException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiInvalidElementAccessException
import com.intellij.psi.impl.DebugUtil

internal class ValidityEvaluatorImpl(
  private val tempProviders: TemporaryProviderStorage,
  private val cache: FileViewProviderCache,
  private val newFileViewProviderFactory: NewFileViewProviderFactory,
) : ValidityEvaluator {

  override fun isRecreatedViewProviderIsIdentical(
    virtualFile: VirtualFile,
    provider: AbstractFileViewProvider,
    context: CodeInsightContext,
  ): Boolean {
    if (tempProviders.contains(virtualFile)) {
      LOG.error(StackOverflowPreventedException("isValid leads to endless recursion in ${provider.javaClass}: ${provider.languages.toList()}"))
    }

    tempProviders.put(virtualFile, null)

    try {
      val recreated = newFileViewProviderFactory.createNewFileViewProviderForValidityCheck(virtualFile, context)
      tempProviders.put(virtualFile, recreated)

      return FileManagerImpl.areViewProvidersEquivalent(provider, recreated) &&
             provider.cachedPsiFiles.all { isValidOriginal(it) }
    }
    finally {
      val temp = tempProviders.remove(virtualFile)
      if (temp is AbstractFileViewProvider) {
        DebugUtil.performPsiModification<Throwable>("invalidate temp view provider") { temp.markInvalidated() }
      }
    }
  }

  private fun bury(
    virtualFile: VirtualFile,
    provider: AbstractFileViewProvider,
    context: CodeInsightContext,
  ) {
    cache.remove(virtualFile, context, provider)
    provider.unmarkPossiblyInvalidated()
  }

  private fun resurrect(
    virtualFile: VirtualFile,
    provider: AbstractFileViewProvider,
    context: CodeInsightContext,
  ) {
    provider.unmarkPossiblyInvalidated()
    val cachedProvider = cache.getRaw(virtualFile, context)
    LOG.assertTrue(cachedProvider == provider, "Cached: $cachedProvider, expected: $provider")

    for (psiFile in provider.cachedPsiFiles) {
      if (!psiFile.isValid) {
        LOG.error(PsiInvalidElementAccessException(psiFile))
      }
    }
  }

  private fun isValidOriginal(file: PsiFile): Boolean {
    val original = file.originalFile
    return original == file || original.isValid
  }

  override fun evaluateValidity(viewProvider: AbstractFileViewProvider): Boolean {
    if (cache.getRaw(viewProvider.virtualFile, viewProvider.codeInsightContext) !== viewProvider) {
      return false
    }

    if (!viewProvider.isPossiblyInvalidated()) return true

    val vFile = viewProvider.virtualFile
    val context = defaultContext()
    if (vFile.isValid && isRecreatedViewProviderIsIdentical(vFile, viewProvider, context)) {
      resurrect(vFile, viewProvider, context)
      return true
    }
    else {
      bury(vFile, viewProvider, context)
      return false
    }
  }

  override fun reanimateProviderIfNecessary(vFile: VirtualFile, viewProvider: FileViewProvider?): FileViewProvider? {
    if (viewProvider !is AbstractFileViewProvider || !viewProvider.isPossiblyInvalidated()) {
      return viewProvider
    }

    tempProviders.get(vFile)?.let {
      return it
    }

    if (!evaluateValidity(viewProvider)) {
      return null
    }

    return viewProvider
  }
}

private val LOG = Logger.getInstance(ValidityEvaluatorImpl::class.java)
