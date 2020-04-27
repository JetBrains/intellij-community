// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper

class UsageSink(private val project: Project) {

  internal fun checkUsages(prevMember: Member?, curMember: Member?, containingFile: PsiFile): List<Problem>? {
    // member properties were changed
    if (prevMember != null && curMember != null && prevMember.name == curMember.name) {
      return processMemberChange(prevMember, curMember, containingFile)
    }
    /**
     * In other cases we need to use module scope since we might already have some problems
     * that are outside of current member scope but that might resolve after the change.
     * @see com.intellij.java.codeInsight.daemon.problems.MethodProblemsTest#testMethodOverrideScopeIsChanged
     * as an example how it might happen.
     */
    val problems = mutableListOf<Problem>()
    // member was renamed or removed
    if (prevMember != null) {
      val memberProblems = processUsages(prevMember, containingFile) ?: return null
      problems.addAll(memberProblems)
    }
    // member was renamed or created
    if (curMember != null) {
      val memberProblems = processUsages(curMember, containingFile) ?: return null
      problems.addAll(memberProblems)
    }
    return problems
  }

  private fun processUsages(member: Member,
                            containingFile: PsiFile,
                            scope: GlobalSearchScope? = extractScope(member, containingFile)): List<Problem>? {
    if (scope == null) return null
    val memberName = member.name
    val isMethodSearch = member.psiMember is PsiMethod
    val usageExtractor: (PsiFile, Int) -> PsiElement? = { psiFile, index ->
      val elementAt = psiFile.findElementAt(index) as? PsiIdentifier
      if (elementAt != null) extractMemberUsage(elementAt, isMethodSearch) else null
    }
    val collector = MemberUsageCollector(memberName, containingFile, usageExtractor)
    PsiSearchHelper.getInstance(project).processAllFilesWithWord(memberName, scope, collector, true)
    val usages = collector.collectedUsages ?: return null
    val problems = mutableListOf<Problem>()
    for (usage in usages) {
      problems.addAll(ProblemMatcher.getProblems(usage))
    }
    return problems
  }

  private fun processMemberChange(prevMember: Member, curMember: Member, containingFile: PsiFile): List<Problem>? {
    val prevScope = prevMember.scope
    val curScope = curMember.scope
    if (prevScope == curScope) return processUsages(curMember, containingFile, curScope)
    val unionScope = prevScope.union(curScope)
    val problems = processUsages(curMember, containingFile, unionScope)
    if (problems != null) return problems
    /**
     * If we reported errors for this member previously and union scope is too big and cannot be analysed,
     * then we stuck with already reported problems even after the fix.
     * To prevent that we need to remove all previously reported problems for this element (even though they are still valid).
     * @see com.intellij.java.codeInsight.daemon.problems.FieldProblemsTest#testErrorsRemovedAfterScopeChanged as an example.
     */
    val prevProblems = processUsages(curMember, containingFile, prevScope)
    // ok, we didn't analyse this element before
    if (prevProblems == null) return null
    return prevProblems.map { if (it.message == null) it else Problem(it.file, null, it.place) }
  }

  companion object {

    private fun extractScope(member: Member, containingFile: PsiFile): GlobalSearchScope? =
      ModuleUtil.findModuleForFile(containingFile)?.moduleScope ?: member.scope

    private fun extractMemberUsage(identifier: PsiIdentifier, isMethodSearch: Boolean): PsiElement? {
      val parent = identifier.parent
      val usage = when {
        parent is PsiReference -> {
          val javaReference = parent.element as? PsiJavaReference
          if (javaReference != null) javaReference as? PsiElement else null
        }
        parent is PsiMethod && isMethodSearch -> parent
        else -> null
      }
      return if (usage is Navigatable) usage else null
    }
  }
}