// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.inference

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.util.gist.GistManager
import com.intellij.util.gist.PsiFileGist

private const val VERSION = 14

/**
 * Holds the [PsiFileGist] for caching contract inference results.
 * <p>
 * This is a separate service to avoid accessing [GistManager] during static class initialization of [InferenceVisitor],
 * which violates platform rules (class initialization must not depend on services).
 * See LSP-407 to learn more.
 */
@Service
internal class ContractInferenceGist {
  val gist: PsiFileGist<Map<Int, MethodData>> get() = _gist
  private val _gist: PsiFileGist<Map<Int, MethodData>>

  constructor() {
    _gist = GistManager.getInstance().newPsiFileGist(
      /* id = */ "contractInference",
      /* version = */ VERSION,
      /* externalizer = */ MethodDataExternalizer,
      /* calcData = */ { psiFile -> indexFile(psiFile.node.lighterAST) },
    )
  }

  companion object {
    @JvmStatic
    fun getInstance(): ContractInferenceGist {
      return ApplicationManager.getApplication().getService(ContractInferenceGist::class.java)
    }
  }
}
