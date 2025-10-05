// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.problems

import com.intellij.psi.*

public class MemberCollector(private val memberFilter: (PsiMember) -> Boolean) : JavaRecursiveElementWalkingVisitor() {

  private val members = mutableListOf<PsiMember>()

  override fun visitMethod(psiMethod: PsiMethod) {
    if (memberFilter(psiMethod)) members.add(psiMethod)
  }

  override fun visitClass(psiClass: PsiClass) {
    if (memberFilter(psiClass)) members.add(psiClass)
    super.visitClass(psiClass)
  }

  override fun visitField(psiField: PsiField) {
    if (memberFilter(psiField)) members.add(psiField)
  }

  override fun visitEnumConstant(psiEnumConstant: PsiEnumConstant) {
    if (memberFilter(psiEnumConstant)) members.add(psiEnumConstant)
  }

  public companion object {
    public fun collectMembers(psiElement: PsiElement, filter: (PsiMember) -> Boolean): List<PsiMember> {
      val collector = MemberCollector(filter)
      psiElement.accept(collector)
      return collector.members
    }
  }
}