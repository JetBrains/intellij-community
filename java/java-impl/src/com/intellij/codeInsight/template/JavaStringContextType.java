// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public class JavaStringContextType extends TemplateContextType {
  public JavaStringContextType() {
    super(JavaPsiBundle.message("context.type.string"));
  }

  @Override
  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    if (PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(JavaLanguage.INSTANCE)) {
      return isStringLiteral(file.findElementAt(offset));
    }
    return false;
  }

  static boolean isStringLiteral(PsiElement element) {
    return PsiUtil.isJavaToken(element, ElementType.STRING_LITERALS);
  }
}
