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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement

abstract class JvmTestDiffProvider<E : PsiElement> : TestDiffProvider {
  final override fun findExpected(project: Project, stackTrace: String): PsiElement? {
    var expectedParamIndex: Int? = null
    var enclosingMethod: UMethod? = null
    val lineParser = ExceptionLineParserFactory.getInstance().create(ExceptionInfoCache(project, GlobalSearchScope.allScope(project)))
    stackTrace.lineSequence().forEach { line ->
      lineParser.execute(line, line.length) ?: return@forEach
      val file = lineParser.file ?: return@forEach
      if (file is PsiBinaryFile) return@forEach
      val virtualFile = file.virtualFile ?: return@forEach
      val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return@forEach
      val lineNumber = lineParser.info.lineNumber
      if (lineNumber < 1) return@forEach
      val startOffset = document.getLineStartOffset(lineNumber - 1)
      val endOffset = document.getLineEndOffset(lineNumber - 1)
      val failedCall = failedCall(file, startOffset, endOffset, enclosingMethod) ?: return@forEach
      val expected = getExpected(failedCall, expectedParamIndex) ?: return@forEach
      enclosingMethod = failedCall.toUElement()?.getParentOfType<UMethod>(true)
      expectedParamIndex = getParamIndex(expected)
      if (expectedParamIndex == null) return expected
    }
    return null
  }

  abstract fun failedCall(file: PsiFile, startOffset: Int, endOffset: Int, method: UMethod?): E?

  abstract fun getParamIndex(param: PsiElement): Int?

  abstract fun getExpected(call: E, argIndex: Int?): PsiElement?
}