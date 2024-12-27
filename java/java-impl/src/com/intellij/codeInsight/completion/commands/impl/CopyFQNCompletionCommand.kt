// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.FqnUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

class CopyFQNCompletionCommand : AbstractActionCompletionCommand("CopyReference",
                                                                 "Copy Reference",
                                                                 IdeBundle.message("copy.reference"),
                                                                 null,
                                                                 -150) {
  override fun supportNonWrittenFiles(): Boolean  = true

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    val element = getContext(offset, psiFile)
    val member = PsiTreeUtil.getParentOfType(element, PsiMember::class.java) ?: return false
    if (member is PsiClass) {
      if (!TextRange(member.textRange.startOffset, member.lBrace?.textRange?.startOffset ?: member.textRange.endOffset)
          .contains(offset)
      ) {
        return false
      }
    }
    else if (member is PsiMethod) {
      if (!TextRange(member.textRange.startOffset, member.body?.textRange?.startOffset ?: member.textRange.endOffset)
          .contains(offset)
      ) {
        return false
      }
    }
    else {
      return false
    }
    return FqnUtil.elementToFqn(element, editor) != null
  }
}
