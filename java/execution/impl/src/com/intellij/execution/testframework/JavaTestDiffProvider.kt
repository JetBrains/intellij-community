// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.execution.testframework.actions.TestDiffProvider
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.file.impl.JavaFileManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.asSafely
import com.intellij.util.containers.ContainerUtil
import com.siyeh.ig.testFrameworks.AssertHint.Companion.createAssertEqualsHint

class JavaTestDiffProvider : TestDiffProvider {
  override fun getInjectionLiteral(project: Project, stackTrace: String): PsiElement? {
    var expectedParamIndex: Int? = null
    parse(stackTrace).forEach { stackFrame ->
      if (stackFrame.location !is JavaStackFrame.FileLocation) return@forEach
      val psiFile = JavaFileManager.getInstance(project)
        .findClass(stackFrame.fqClassName, GlobalSearchScope.projectScope(project))
        ?.containingFile ?: return@forEach
      val virtualFile = psiFile.virtualFile ?: return@forEach
      val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return@forEach
      val startOffset = document.getLineStartOffset(stackFrame.location.lineNumber - 1)
      val endOffset = document.getLineEndOffset(stackFrame.location.lineNumber - 1)
      val failedCall = getFailedCall(psiFile, startOffset, endOffset) ?: return@forEach
      val expected = getExpected(failedCall, expectedParamIndex) ?: return@forEach
      if (expected is PsiParameter) {
        expectedParamIndex = expected.parent.asSafely<PsiParameterList>()?.parameters?.indexOf<PsiElement>(expected)
      } else {
        return InjectedLanguageManager.getInstance(project).findInjectedElementAt(psiFile, expected.textOffset + 1)
      }
    }
    return null
  }

  private fun parse(stackStrace: String): Sequence<JavaStackFrame> {
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
            val lineNumber = location.substringAfter(':').toInt()
            return FileLocation(fileName, lineNumber)
          }
        }
      }
    }

    object NativeLocation : Location

    data class FileLocation(val fileName: String, val lineNumber: Int) : Location

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

  private fun getFailedCall(psiFile: PsiFile, startOffset: Int, endOffset: Int): PsiMethodCallExpression? {
    val statements = CodeInsightUtil.findStatementsInRange(psiFile, startOffset, endOffset)
    if (statements.isEmpty()) return null
    if (statements.size > 1 && statements[0] !is PsiExpressionStatement) return null
    val expression = (statements[0] as PsiExpressionStatement).expression
    return if (expression !is PsiMethodCallExpression) null else expression
  }

  private fun getExpected(callExpression: PsiMethodCallExpression, argument: Int?): PsiElement? {
    val expression = if (argument == null) {
      val hint = createAssertEqualsHint(callExpression) ?: return null
      hint.expected
    } else {
      callExpression.argumentList.expressions[argument]
    }
    if (expression is PsiLiteralExpression) return expression
    if (expression is PsiPolyadicExpression
        && ContainerUtil.all(expression.operands) { obj: PsiExpression? -> PsiLiteralExpression::class.java.isInstance(obj) }
    ) return expression
    if (expression is PsiReference) return expression.resolve()
    return null
  }
}