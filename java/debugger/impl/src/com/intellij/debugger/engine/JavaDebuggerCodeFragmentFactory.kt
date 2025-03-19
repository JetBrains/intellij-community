// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.evaluation.CodeFragmentFactory
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

abstract class JavaDebuggerCodeFragmentFactory : CodeFragmentFactory() {
  final override fun createPsiCodeFragment(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment? {
    val codeFragment = createPsiCodeFragmentImpl(item, context, project) ?: return null
    if (context != null) {
      setThisType(context, codeFragment)
    }
    return codeFragment
  }


  final override fun createPresentationPsiCodeFragment(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment? {
    val codeFragment = createPresentationPsiCodeFragmentImpl(item, context, project) ?: return null
    if (context != null) {
      setThisType(context, codeFragment)
    }
    return codeFragment
  }

  protected abstract fun createPresentationPsiCodeFragmentImpl(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment?
  protected abstract fun createPsiCodeFragmentImpl(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment?

  private fun setThisType(context: PsiElement, codeFragment: JavaCodeFragment) {
    var contextType = context.getUserData(DebuggerUtilsImpl.PSI_TYPE_KEY)
    if (contextType == null) {
      val contextClass = PsiTreeUtil.getNonStrictParentOfType(context, PsiClass::class.java)
      if (contextClass != null) {
        contextType = JavaPsiFacade.getElementFactory(codeFragment.getProject()).createType(contextClass)
      }
    }
    codeFragment.setThisType(contextType)
  }
}
