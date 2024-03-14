// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.vfs.VirtualFile

internal fun createIntersectionCodeInsightContextInfo(scope1: GlobalSearchScope, scope2: GlobalSearchScope): CodeInsightContextInfo {
  val info1 = (scope1 as? CodeInsightContextAwareSearchScope)?.codeInsightContextInfo as? ActualCodeInsightContextInfo
  val info2 = (scope2 as? CodeInsightContextAwareSearchScope)?.codeInsightContextInfo as? ActualCodeInsightContextInfo

  if (info1 == null && info2 == null) {
    return NoContextInformation()
  }

  if (info1 == null) {
    requireNotNull(info2)
    return CodeInsightContextInfoIntersection1(info2, scope1)
  }
  if (info2 == null) {
    return CodeInsightContextInfoIntersection1(info1, scope2)
  }
  return CodeInsightContextInfoIntersection(info1, info2)
}

private fun createIntersectionFileInfo(info1: CodeInsightContextFileInfo, info2: CodeInsightContextFileInfo): CodeInsightContextFileInfo {
  if (info1 is DoesNotContainFileInfo || info2 is DoesNotContainFileInfo) {
    return DoesNotContainFileInfo()
  }

  if (info1 is NoContextFileInfo || info2 is NoContextFileInfo) {
    return NoContextFileInfo()
  }

  require(info1 is ActualContextFileInfo && info2 is ActualContextFileInfo)

  val contexts1 = info1.contexts
  val contexts2 = info2.contexts

  val intersection = contexts1.intersect(contexts2)
  if (intersection.isEmpty()) {
    return DoesNotContainFileInfo()
  }
  return ActualContextFileInfo(intersection)
}

private class CodeInsightContextInfoIntersection(
  private val info1: ActualCodeInsightContextInfo,
  private val info2: ActualCodeInsightContextInfo,
): ActualCodeInsightContextInfo {
  override fun contains(file: VirtualFile, context: CodeInsightContext): Boolean {
    return info1.contains(file, context) && info2.contains(file, context)
  }

  override fun getFileInfo(file: VirtualFile): CodeInsightContextFileInfo {
    val fileInfo1 = info1.getFileInfo(file)
    val fileInfo2 = info2.getFileInfo(file)
    return createIntersectionFileInfo(fileInfo1, fileInfo2)
  }
}

private class CodeInsightContextInfoIntersection1(
  private val info1: ActualCodeInsightContextInfo,
  private val scope2: GlobalSearchScope
) : ActualCodeInsightContextInfo {
  override fun contains(file: VirtualFile, context: CodeInsightContext): Boolean {
    return info1.contains(file, context) && scope2.contains(file)
  }

  override fun getFileInfo(file: VirtualFile): CodeInsightContextFileInfo {
    if (!scope2.contains(file)) {
      return DoesNotContainFileInfo()
    }

    return info1.getFileInfo(file)
  }
}