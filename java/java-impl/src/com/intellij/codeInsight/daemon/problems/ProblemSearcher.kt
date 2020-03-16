// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

internal class ProblemSearcher(private val file: PsiFile): JavaElementVisitor() {

    private val problems = mutableSetOf<PsiElement>()

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
      paramsList.attributes.forEach {
        val detachedValue = it.detachedValue
        if (detachedValue != null) findProblem(detachedValue)
        findProblem(it)
      }
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

    override fun visitForeachStatement(statement: PsiForeachStatement) {
      visitStatement(statement)
      findProblem(statement.iterationParameter)
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
      val problem = problemHolder.problem
      if (problem != null) {
        val statement = PsiTreeUtil.getParentOfType(element, PsiStatement::class.java, false,
                                                    PsiClass::class.java, PsiMethod::class.java, PsiVariable::class.java)
        problems.add(statement ?: element)
      }
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
        return problem != null
      }

      private fun findPlace(info: HighlightInfo): PsiElement? {
        val startElement = file.findElementAt(info.actualStartOffset) ?: return null
        val endElement = file.findElementAt(info.actualEndOffset - 1) ?: return null
        return PsiTreeUtil.findCommonParent(startElement, endElement)
      }
    }

  companion object {

    internal fun getProblems(usage: PsiElement): Set<PsiElement> {
      val startElement = getSearchStartElement(usage) ?: return emptySet()
      val psiFile = startElement.containingFile
      val searcher = ProblemSearcher(psiFile)
      startElement.accept(searcher)
      return searcher.problems
    }

    private fun getSearchStartElement(usage: PsiElement) = when (usage) {
      is PsiMethod -> usage.modifierList.findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE) ?: usage
      is PsiJavaCodeReferenceElement -> usage.referenceNameElement
      else -> usage
    }
  }
}