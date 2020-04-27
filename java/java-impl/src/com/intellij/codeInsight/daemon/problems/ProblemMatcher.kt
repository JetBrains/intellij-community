// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

internal class ProblemMatcher {

  private class ProblemSearcher(private val file: PsiFile): JavaElementVisitor() {

    val problems = mutableMapOf<Navigatable, String?>()

    override fun visitElement(element: PsiElement) {
      findProblem(element)
      element.parent?.accept(this)
    }

    override fun visitReferenceExpression(expression: PsiReferenceExpression) {
      visitElement(expression)
    }

    override fun visitCallExpression(callExpression: PsiCallExpression) {
      val args = callExpression.argumentList
      if (args != null) findProblem(args)
      visitElement(callExpression)
    }

    override fun visitAnnotation(annotation: PsiAnnotation) {
      val paramsList = annotation.parameterList
      paramsList.attributes.forEach { findProblem(it) }
      visitElement(annotation)
    }

    override fun visitParameter(parameter: PsiParameter) {
      val parent = parameter.parent
      if (parent is PsiCatchSection) findProblem(parent.tryStatement)
      findProblem(parameter)
    }

    override fun visitVariable(variable: PsiVariable) {
      findProblem(variable)
    }

    override fun visitStatement(statement: PsiStatement) {
      findProblem(statement)
    }

    override fun visitMethod(method: PsiMethod) {
      findProblem(method)
      val typeElement = method.returnTypeElement
      if (typeElement != null) findProblem(typeElement)
      val params = method.parameterList
      findProblem(params)
      val modifiers = method.modifierList
      findProblem(modifiers)
    }

    override fun visitAnonymousClass(aClass: PsiAnonymousClass) {
      visitElement(aClass)
    }

    override fun visitClass(psiClass: PsiClass) {
      val modifiers = psiClass.modifierList
      if (modifiers != null) findProblem(modifiers)
      findProblem(psiClass)
    }

    private fun findProblem(element: PsiElement) {
      if (element !is Navigatable) return
      val problemHolder = ProblemHolder(file)
      val visitor = object : HighlightVisitorImpl() {
        init {
          prepareToRunAsInspection(problemHolder)
        }
      }
      element.accept(visitor)
      problems[element] = problemHolder.problem
    }

    private class ProblemHolder(private val file: PsiFile): HighlightInfoHolder(file) {

      var problem: String? = null

      override fun add(info: HighlightInfo?): Boolean {
        if (problem != null || info == null || info.severity != HighlightSeverity.ERROR) return true
        val place = findPlace(info)
        if (place !is Navigatable) return true
        problem = info.description
        return true
      }

      override fun hasErrorResults(): Boolean {
        return false
      }

      private fun findPlace(info: HighlightInfo): PsiElement? {
        val startElement = file.findElementAt(info.actualStartOffset) ?: return null
        val endElement = file.findElementAt(info.actualEndOffset - 1) ?: return null
        return PsiTreeUtil.findCommonParent(startElement, endElement)
      }
    }
  }

  companion object {

    internal fun getProblems(usage: PsiElement): List<Problem> {
      val startElement = getSearchStartElement(usage) ?: return emptyList()
      val psiFile = startElement.containingFile
      return collectProblems(psiFile, startElement)
    }

    private fun collectProblems(psiFile: PsiFile, startElement: PsiElement): List<Problem> {
      val searcher = ProblemSearcher(psiFile)
      startElement.accept(searcher)
      val file = psiFile.virtualFile
      return searcher.problems.map { Problem(file, it.value, it.key) }
    }

    private fun getSearchStartElement(usage: PsiElement) = when (usage) {
      is PsiMethod -> usage.modifierList.findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE) ?: usage
      is PsiJavaCodeReferenceElement -> usage.referenceNameElement
      else -> usage
    }
  }
}