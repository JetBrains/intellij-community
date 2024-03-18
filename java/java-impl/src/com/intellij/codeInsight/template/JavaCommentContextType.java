// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public final class JavaCommentContextType extends TemplateContextType {
  public JavaCommentContextType() {
    super(JavaPsiBundle.message("context.type.java.comment"));
  }

  @Override
  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    if (PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(JavaLanguage.INSTANCE)) {
      PsiElement element = file.findElementAt(offset);
      if (element instanceof PsiWhiteSpace && offset > 0) {
        element = file.findElementAt(offset-1);
      }
      return PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null;
    }
    return false;
  }
}
