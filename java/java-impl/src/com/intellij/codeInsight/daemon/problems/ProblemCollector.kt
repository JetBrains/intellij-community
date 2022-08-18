// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope


internal class ProblemCollector {

  companion object {

    /**
     * Collect broken usages based on current and previous state of an element.
     * Broken usages are searched in a union scope of previous member state and current member state.
     * In order to determine if usage is broken or not we run highlighter (see ProblemSearcher for details)
     */
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
      val memberType = MemberType.create(curMember) ?: return null
      val memberName = prevMember.name
      return processUsages(memberName, memberType, containingFile, unionScope)
    }

    private fun processUsages(containingFile: PsiFile, psiMember: PsiMember): Set<Problem>? {
      val memberName = psiMember.name ?: return null
      val scope = psiMember.useScope as? GlobalSearchScope ?: return null
      val memberType = MemberType.create(psiMember) ?: return null
      return processUsages(memberName, memberType, containingFile, scope)
    }

    private fun processUsages(containingFile: PsiFile, member: ScopedMember): Set<Problem>? {
      val scope = member.scope as? GlobalSearchScope ?: return null
      val memberName = member.name
      val memberType = MemberType.create(member.member)
      return processUsages(memberName, memberType, containingFile, scope)
    }

    private fun processUsages(memberName: String,
                              memberType: MemberType,
                              containingFile: PsiFile,
                              scope: GlobalSearchScope): Set<Problem>? {
      val usageExtractor: (PsiFile, Int) -> PsiElement? = { file, index -> extractUsage(file, index, memberType) }
      val usages = MemberUsageCollector.collect(memberName, containingFile, scope, usageExtractor) ?: return null
      return usages.flatMapTo(mutableSetOf()) { ProblemSearcher.getProblems(it, containingFile, memberType) }
    }

    private fun extractUsage(psiFile: PsiFile, index: Int, memberType: MemberType): PsiElement? {
      val identifier = psiFile.findElementAt(index) as? PsiIdentifier ?: return null
      val parent = identifier.parent
      val usage = when {
        parent is PsiReference -> {
          val javaReference = parent.element as? PsiJavaReference
          if (javaReference != null) javaReference as? PsiElement else null
        }
        parent is PsiMethod && memberType == MemberType.METHOD -> parent
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