// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper


internal class ProblemCollector {

  companion object {

    @JvmName("collect")
    @JvmStatic
    internal fun collect(prevMember: ScopedMember?, curMember: PsiMember): Set<Problem>? {
      val containingFile = curMember.containingFile

      // member properties were changed
      if (prevMember != null && prevMember.name == curMember.name) {
        return processMemberChange(prevMember, curMember, containingFile)
      }

      val problems = mutableSetOf<Problem>()

      // member was renamed
      if (prevMember != null) {
        val memberProblems = processUsages(containingFile, prevMember) ?: return null
        problems.addAll(memberProblems)
      }

      val memberProblems = processUsages(containingFile, curMember) ?: return null
      problems.addAll(memberProblems)

      return problems
    }

    private fun processMemberChange(prevMember: ScopedMember, curMember: PsiMember, containingFile: PsiFile): Set<Problem>? {
      val unionScope = getUnionScope(prevMember, curMember) ?: return null
      val memberName = prevMember.name
      val isMethodSearch = curMember is PsiMethod
      return processUsages(memberName, isMethodSearch, containingFile, unionScope)
    }

    private fun processUsages(containingFile: PsiFile, psiMember: PsiMember): Set<Problem>? {
      val memberName = psiMember.name ?: return null
      val scope = psiMember.useScope as? GlobalSearchScope ?: return null
      val isMethodSearch = psiMember is PsiMethod
      return processUsages(memberName, isMethodSearch, containingFile, scope)
    }

    private fun processUsages(containingFile: PsiFile, member: ScopedMember): Set<Problem>? {
      val scope = member.scope as? GlobalSearchScope ?: return null
      val memberName = member.name
      val isMethodSearch = member.member is Member.Method
      return processUsages(memberName, isMethodSearch, containingFile, scope)
    }

    private fun processUsages(memberName: String,
                              isMethodSearch: Boolean,
                              containingFile: PsiFile,
                              scope: GlobalSearchScope): Set<Problem>? {
      val usageExtractor: (PsiFile, Int) -> PsiElement? = { file, index -> extractUsage(file, index, isMethodSearch) }
      val project = containingFile.project
      val collector = MemberUsageCollector(memberName, project, usageExtractor)
      PsiSearchHelper.getInstance(project).processAllFilesWithWord(memberName, scope, collector, true)
      val usages = collector.collectedUsages ?: return null
      return usages.flatMapTo(mutableSetOf()) { ProblemSearcher.getProblems(it, containingFile) }
    }

    private fun extractUsage(psiFile: PsiFile, index: Int, isMethodSearch: Boolean): PsiElement? {
      val identifier = psiFile.findElementAt(index) as? PsiIdentifier ?: return null
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

    private fun getUnionScope(prevMember: ScopedMember, curMember: PsiMember): GlobalSearchScope? {
      val prevScope = prevMember.scope as? GlobalSearchScope
      val curScope = curMember.useScope as? GlobalSearchScope ?: return prevScope
      if (prevScope == null) return curScope
      return if (prevScope == curScope) return curScope else prevScope.union(curScope)
    }
  }
}