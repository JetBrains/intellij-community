// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.execution.filters.ExceptionInfoCache
import com.intellij.execution.filters.ExceptionLineParserFactory
import com.intellij.execution.testframework.actions.TestDiffProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope

abstract class JvmTestDiffProvider<E : PsiElement> : TestDiffProvider {
  final override fun findExpected(project: Project, stackTrace: String): PsiElement? {
    var expectedParamIndex: Int? = null
    val lineParser = ExceptionLineParserFactory.getInstance().create(ExceptionInfoCache(project, GlobalSearchScope.allScope(project)))
    stackTrace.lineSequence().forEach {  line ->
      lineParser.execute(line, line.length) ?: return@forEach
      val file = lineParser.file ?: return@forEach
      if (file is PsiBinaryFile) return@forEach
      val virtualFile = file.virtualFile ?: return@forEach
      val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return@forEach
      val lineNumber = lineParser.info.lineNumber
      val startOffset = document.getLineStartOffset(lineNumber - 1)
      val endOffset = document.getLineEndOffset(lineNumber - 1)
      val failedCall = getFailedCall(file, startOffset, endOffset) ?: return@forEach
      val expected = getExpected(failedCall, expectedParamIndex) ?: return@forEach
      expectedParamIndex = getParamIndex(expected)
      if (expectedParamIndex == null) return expected
    }
    return null
  }

  abstract fun getParamIndex(param: PsiElement): Int?

  abstract fun getFailedCall(file: PsiFile, startOffset: Int, endOffset: Int): E?

  abstract fun getExpected(call: E, argIndex: Int?): PsiElement?
}