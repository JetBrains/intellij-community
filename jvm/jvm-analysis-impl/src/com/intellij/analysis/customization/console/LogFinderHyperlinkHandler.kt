// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.customization.console

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.codeInspection.logging.*
import com.intellij.execution.filters.HyperlinkInfoFactory
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.annotations.Nls
import org.jetbrains.uast.*

internal class LogFinderHyperlinkHandler(private val probableClassName: ProbableClassName) : HyperlinkInfoFactory.HyperlinkHandler {
  override fun onLinkFollowed(project: Project, file: VirtualFile, targetEditor: Editor, originalEditor: Editor?) {
    LogConsoleLogHandlerCollectors.logHandleClass(project, probableClassName.virtualFiles.size)

    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile == null) return
    val uElement = psiFile.toUElement()
    if (uElement == null) return
    val visitor = underModalProgress(project, CodeInsightBundle.message("progress.title.resolving.reference")) {
      val logVisitor = LogVisitor(probableClassName)
      psiFile.accept(logVisitor)
      logVisitor
    }

    if (visitor.similarClasses.isNotEmpty()) {
      val uClass = visitor.similarClasses.minBy { it.sourcePsi?.textRange?.startOffset ?: Int.MAX_VALUE }
      val sourcePsi = uClass.uastAnchor?.sourcePsi
      if (sourcePsi != null) {
        EditSourceUtil.navigateToPsiElement(sourcePsi)
      }
    }
    else {
      return
    }

    if (!visitor.similarCalls.isEmpty()) {
      navigateToCalls(visitor, project, targetEditor)
    }
  }

  private fun navigateToCalls(visitor: LogVisitor,
                              project: Project,
                              targetEditor: Editor) {
    if (visitor.similarCalls.size == 1) {
      val sourcePsi = visitor.similarCalls.first().sourcePsi ?: return
      EditSourceUtil.navigateToPsiElement(sourcePsi)
      LogConsoleLogHandlerCollectors.logHandleLogCalls(project, 1)
    }
    else {
      val targetElements = visitor.similarCalls.mapNotNull { it.sourcePsi }.toList()
      PsiTargetNavigator(targetElements)
        .presentationProvider { element ->
          TargetPresentation.builder(getText(element))
            .containerText(getContainerText(element))
            .presentation()
        }
        .elementsConsumer { elements, navigator ->
          if (!elements.isEmpty()) {
            val message = JvmAnalysisBundle.message("jvm.class.filter.choose.calls")
            navigator.title(message)
            navigator.tabTitle(message)
          }
        }
        .navigate(targetEditor, null) {
          LogConsoleLogHandlerCollectors.logHandleLogCalls(project, targetElements.size)
          EditSourceUtil.navigateToPsiElement(it)
        }
    }
  }

  private fun getContainerText(element: PsiElement): @NlsSafe String {
    val method = element.toUElement()?.getParentOfType<UMethod>() ?: return ""
    val className = method.getParentOfType<UClass>()?.javaPsi?.name ?: ""
    val document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.containingFile) ?: return ""
    val lineNumber = document.getLineNumber(element.textRange.endOffset)
    val container = className + "." + method.name + "(): " + (lineNumber + 1)
    return StringUtil.shortenTextWithEllipsis(container, 40, 10)
  }

  @Nls
  private fun getText(element: PsiElement): String {
    val text = element.text
    val document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.containingFile) ?: return text
    val lineNumber = document.getLineNumber(element.textRange.endOffset)
    val textRange = element.textRange
      .intersection(TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber))) ?: element.textRange
    val trimmedText = document.getText(textRange).trim()
    return StringUtil.shortenTextWithEllipsis(trimmedText, 30, 0)
  }
}

private class LogVisitor(private val probableClassName: ProbableClassName) : PsiRecursiveElementVisitor() {
  val similarClasses = mutableSetOf<UClass>()
  val similarCalls = mutableSetOf<UCallExpression>()
  override fun visitElement(element: PsiElement) {
    val uClass = element.toUElementOfType<UClass>()
    if (uClass != null &&
        probableClassName.shortClassName == uClass.javaPsi.name) {
      similarClasses.add(uClass)
    }
    val uCall = element.toUElementOfType<UCallExpression>()
    if (uCall != null && checkCalls(uCall, probableClassName)) {
      similarCalls.add(uCall)
    }
    super.visitElement(element)
  }

  private fun checkCalls(uCall: UCallExpression, probableClassName: ProbableClassName): Boolean {
    val isLogger = LoggingUtil.LOG_MATCHERS.uCallMatches(uCall) ||
                   LoggingUtil.LEGACY_LOG_MATCHERS.uCallMatches(uCall) ||
                   LoggingUtil.IDEA_LOG_MATCHER.uCallMatches(uCall)
    if (!isLogger) {
      return false
    }
    val method = uCall.resolveToUElementOfType<UMethod>() ?: return false
    val arguments = uCall.valueArguments
    val parameters = method.uastParameters
    val index = getLogStringIndex(parameters)
    var logStringArgument: UExpression? = null
    if (index == null) {
      val searcher = LOGGER_TYPE_SEARCHERS.mapFirst(uCall)
      if (searcher != null) {
        logStringArgument = findMessageSetterStringArg(uCall, searcher)
      }
    }
    else {
      logStringArgument = arguments[index - 1]
    }
    if (logStringArgument == null && parameters.isNotEmpty()) logStringArgument = arguments[0]
    if (logStringArgument == null) return false
    val calculateValue = LoggingStringPartEvaluator.calculateValue(logStringArgument) ?: return false
    val fullLine = probableClassName.fullLine
    val classFullName = probableClassName.packageName + "." + probableClassName.shortClassName
    var startPoint = probableClassName.fullLine.indexOf(classFullName)
    if (startPoint == -1) return false
    startPoint += classFullName.length
    if (calculateValue.none { it.isConstant && it.text != null }) return false
    for (value in calculateValue) {
      if (value.isConstant && value.text != null) {
        for (part in value.text.split("{}")) {
          startPoint = fullLine.indexOf(part, startPoint)
          if (startPoint == -1) return false
          startPoint += part.length
        }
      }
    }
    return true
  }
}
