// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.completion.commands.api.Command
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.parents
import javax.swing.Icon

class FormatCodeCommand : Command() {
  override val name: String
    get() = "format"

  override val icon: Icon
    get() = AllIcons.Actions.ReformatCode // Use the reformat icon

  override fun isApplicable(offset: Int, psiFile: PsiFile): Boolean {
    // Always applicable
    return true
  }

  override fun execute(offset: Int, psiFile: PsiFile) {
    val element = getContext(offset, psiFile) ?: return
    val target = element.parents(true).first { it is PsiMember || it is PsiCodeBlock || it is PsiStatement }
    ReformatCodeProcessor(element.containingFile, arrayOf(target.textRange)).run()
  }
}