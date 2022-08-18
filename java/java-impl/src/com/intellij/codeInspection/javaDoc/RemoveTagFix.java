// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

class RemoveTagFix implements LocalQuickFix {
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
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiDocTag tag = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiDocTag.class);
    if (tag != null) {
      tag.delete();
    }
  }
}
