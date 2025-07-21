// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.java.codeserver.highlighting.JavaErrorCollector
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Pair of reported element and context. 
 * Context is mainly used for display purposes.
 */
public class Problem(public val reportedElement: PsiElement, public val context: PsiElement) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Problem

    return context == other.context
  }

  override fun hashCode(): Int {
    return context.hashCode()
  }
}

internal class ProblemSearcher(private val file: PsiFile, private val memberType: MemberType) : JavaElementVisitor() {

  private val problems = mutableSetOf<Problem>()
  private var seenReference = false

  override fun visitElement(element: PsiElement) {
    findProblem(element)
    if (element != file) {
      element.parent?.accept(this)
    }
  }

  override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
    if (seenReference && reference.resolve() != null && memberType != MemberType.FIELD) return
    seenReference = true
    super.visitReferenceElement(reference)
  }

  override fun visitReferenceExpression(expression: PsiReferenceExpression) {
    visitReferenceElement(expression)
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
    val element = statement.iterationParameter
    findProblem(element)
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
    val nameIdentifier = psiClass.nameIdentifier
    if (nameIdentifier != null) findProblem(nameIdentifier)
    findProblem(psiClass)
  }

  private fun findProblem(element: PsiElement) {
    if (element !is Navigatable || element is SyntheticElement || element is LightElement) return
    val error = JavaErrorCollector.findSingleError(element) ?: return
    val context = PsiTreeUtil.getNonStrictParentOfType(element, PsiStatement::class.java,
                                                       PsiClass::class.java, PsiMethod::class.java, PsiVariable::class.java) ?: element
    problems.add(Problem(error.psi(), context))
  }

  companion object {

    /**
     * Checks if usage is broken due to change in target file.
     * 
     * To understand if usage is broken we visit this usage with {@link HighlightVisitorImpl} and check if highlighter reported any problems
     * It is possible that one broken usage led to multiple problems.
     */
    internal fun getProblems(usage: PsiElement, targetFile: PsiFile, memberType: MemberType): Set<Problem> {
      val startElement = getSearchStartElement(usage, targetFile) ?: return emptySet()
      val psiFile = startElement.containingFile
      val searcher = ProblemSearcher(psiFile, memberType)
      startElement.accept(searcher)
      return searcher.problems
    }

    private fun getSearchStartElement(usage: PsiElement, targetFile: PsiFile): PsiElement? {
      when (usage) {
        is PsiMethod -> return usage.modifierList.findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE) ?: usage
        is PsiJavaCodeReferenceElement -> {
          val resolveResult = usage.advancedResolve(false)
          if (resolveResult.isValidResult) {
            val containingFile = resolveResult.element?.containingFile
            if (containingFile != null && containingFile != targetFile) return null
          }
          return usage.referenceNameElement
        }
        else -> return usage
      }
    }
  }
}