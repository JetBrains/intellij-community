// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

class RemoveTagFix extends PsiUpdateModCommandQuickFix {
  private final String myTagName;

  RemoveTagFix(String tagName) {
    myTagName = tagName;
  }

  @NotNull
  @Override
  public String getName() {
    return JavaBundle.message("quickfix.text.remove.javadoc.0", myTagName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("quickfix.family.remove.javadoc.tag");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiDocTag tag = PsiTreeUtil.getParentOfType(element, PsiDocTag.class);
    if (tag != null) {
      tag.delete();
    }
  }
}
