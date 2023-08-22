// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiElement


abstract class IntroduceActionBase : BasePlatformRefactoringAction() {
  init {
    setInjectedContext(true)
  }

  override fun isAvailableInEditorOnly(): Boolean = true

  override fun isEnabledOnElements(elements: Array<out PsiElement>): Boolean = false

  override fun update(e: AnActionEvent) {
    super.update(e)
    ExtractSuperActionBase.removeFirstWordInMainMenu(this, e)
  }
}

