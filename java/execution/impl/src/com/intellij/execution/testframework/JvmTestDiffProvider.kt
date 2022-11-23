// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.execution.testframework.actions.TestDiffProvider
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.asSafely

abstract class JvmTestDiffProvider<E : PsiElement> : TestDiffProvider {
  final override fun findExpected(project: Project, stackTrace: String): PsiElement? {
    var expectedParamIndex: Int? = null
    parseStackTrace(stackTrace).forEach { stackFrame ->
      val location = stackFrame.location
      if (location !is JavaStackFrame.FileLocation) return@forEach
      val file = JavaPsiFacade.getInstance(project)
        .findClass(stackFrame.fqClassName, GlobalSearchScope.projectScope(project))
        ?.containingFile?.navigationElement?.asSafely<PsiFile>() ?: return@forEach
      val virtualFile = file.virtualFile ?: return@forEach
      val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return@forEach
      val lineNumber = location.lineNumber ?: return@forEach
      val startOffset = document.getLineStartOffset(lineNumber - 1)
      val endOffset = document.getLineEndOffset(lineNumber - 1)
      val failedCall = getFailedCall(file, startOffset, endOffset) ?: return@forEach
      val expected = getExpected(failedCall, expectedParamIndex) ?: return@forEach
      expectedParamIndex = getParamIndex(expected)
      return InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, expected.textOffset + 1)
    }
    return null
  }

  abstract fun getParamIndex(param: PsiElement): Int?

  abstract fun getFailedCall(file: PsiFile, startOffset: Int, endOffset: Int): E?

  abstract fun getExpected(call: E, argIndex: Int?): PsiElement?

  private fun parseStackTrace(stackStrace: String): Sequence<JavaStackFrame> {
    return stackStrace.lineSequence().mapNotNull { stackFrame ->
      if (stackFrame.isEmpty()) null else JavaStackFrame.parse(stackFrame)
    }
  }

  private data class JavaStackFrame(
    val fqModuleName: String?,
    val fqClassName: String,
    val methodName: String,
    val location: Location
  ) {
    sealed interface Location {
      companion object {
        fun parse(location: String): Location {
          if (location == "Native Method") {
            return NativeLocation
          } else {
            val fileName = location.substringBefore(':')
            val lineNumber = location.substringAfter(':').toIntOrNull()
            return FileLocation(fileName, lineNumber)
          }
        }
      }
    }

    object NativeLocation : Location

    data class FileLocation(val fileName: String, val lineNumber: Int?) : Location

    companion object {
      fun parse(line: String): JavaStackFrame {
        val strippedFrame = line.substringAfter("at ")
        val signature = strippedFrame.substringBefore('(')
        val location = Location.parse(strippedFrame.substringAfter('(').removeSuffix(")"))
        val pathToClass = signature.substringBeforeLast('.')
        val (fqModuleName, fqClassName) = if (pathToClass.contains('/')) {
          val splits = pathToClass.split('/')
          splits.first() to splits.last()
        } else {
          null to pathToClass
        }
        val methodName = signature.substringAfterLast('.')
        return JavaStackFrame(fqModuleName, fqClassName, methodName, location)
      }
    }
  }
}