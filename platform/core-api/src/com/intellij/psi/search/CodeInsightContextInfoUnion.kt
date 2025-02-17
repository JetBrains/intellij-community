// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.vfs.VirtualFile

internal fun createCodeInsightContextInfoUnion(scopes: Array<out GlobalSearchScope>): CodeInsightContextInfo {
  if (scopes.isEmpty() || scopes.none { scope -> scope is CodeInsightContextAwareSearchScope }) {
    return NoContextInformation()
  }
  return CodeInsightContextInfoUnion(scopes)
}

// todo ijpl-339 what shall we do if two scopes contain a file,
//  but only one of the scopes is context-aware,
//  so we get contexts only from the another one.
//  shall we somehow inform about that???
private class CodeInsightContextInfoUnion(
  private val scopes: Array<out GlobalSearchScope>
) : ActualCodeInsightContextInfo {
  override fun contains(file: VirtualFile, context: CodeInsightContext): Boolean {
    return scopes.any { scope ->
      if (scope is CodeInsightContextAwareSearchScope) {
        when (val info = scope.codeInsightContextInfo) {
          is ActualCodeInsightContextInfo -> info.contains(file, context)
          is NoContextInformation -> scope.contains(file)
        }
      }
      else {
        scope.contains(file)
      }
    }
  }

  override fun getFileInfo(file: VirtualFile): CodeInsightContextFileInfo {
    val actualScopes = scopes.filter { it.contains(file) }
    if (actualScopes.isEmpty()) {
      return DoesNotContainFileInfo()
    }

    val contextInfos = actualScopes
      .filterIsInstance<CodeInsightContextAwareSearchScope>()
      .map { scope -> scope.codeInsightContextInfo }

    // we can safely skip all "no-context-info" as they don't make any difference.
    // anyway, if there's no real context available, we will add `anyContext`

    val actualFileInfos = contextInfos.filterIsInstance<ActualCodeInsightContextInfo>()
      .map { info -> info.getFileInfo(file) }
      .filterIsInstance<ActualContextFileInfo>()

    val knownContexts = actualFileInfos
      .flatMap { info -> info.contexts }
      .distinct()

    return createContainingContextFileInfo(knownContexts)
  }
}