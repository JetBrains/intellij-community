// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.execution.filters.ExceptionInfoCache
import com.intellij.execution.filters.ExceptionLineParserFactory
import com.intellij.execution.testframework.actions.TestDiffProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.asSafely
import org.jetbrains.uast.*

abstract class JvmTestDiffProvider : TestDiffProvider {
  final override fun findExpected(project: Project, stackTrace: String): PsiElement? {
    var expectedParam: UParameter? = null
    var enclosingMethod: UMethod? = null
    val lineParser = ExceptionLineParserFactory.getInstance().create(ExceptionInfoCache(project, GlobalSearchScope.allScope(project)))
    stackTrace.lineSequence().forEach { line ->
      lineParser.execute(line, line.length) ?: return@forEach
      val file = lineParser.file ?: return@findExpected null
      val diffProvider = TestDiffProvider.TEST_DIFF_PROVIDER_LANGUAGE_EXTENSION.forLanguage(file.language).asSafely<JvmTestDiffProvider>()
      if (diffProvider == null) return@findExpected null
      if (diffProvider.isCompiled(file)) {
        if (expectedParam == null) return@forEach // keep looking, test class wasn't found yet
        else return@findExpected null // return null when tracking expected param
      }
      val virtualFile = file.virtualFile ?: return@findExpected null
      val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return@findExpected null
      val lineNumber = lineParser.info.lineNumber
      if (lineNumber < 1 || lineNumber > document.lineCount) return@findExpected null
      val startOffset = document.getLineStartOffset(lineNumber - 1)
      val endOffset = document.getLineEndOffset(lineNumber - 1)
      val failedCall = diffProvider.failedCall(file, startOffset, endOffset, enclosingMethod) ?: return@findExpected null
      val expected = diffProvider.getExpected(failedCall, expectedParam) ?: return@findExpected null
      enclosingMethod = failedCall.toUElement()?.getParentOfType<UMethod>(true)
      expectedParam = expected.toUElementOfType<UParameter>()
      if (expectedParam == null) return expected
    }
    return null
  }

  abstract fun isCompiled(file: PsiFile): Boolean

  abstract fun failedCall(file: PsiFile, startOffset: Int, endOffset: Int, method: UMethod?): PsiElement?

  abstract fun getExpected(call: PsiElement, param: UParameter?): PsiElement?
}