// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiTypeElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
final class JavaTypeParameterUnwrapper extends JavaUnwrapper {

  JavaTypeParameterUnwrapper() {
    super(JavaBundle.message("unwrap.type.parameter"));
  }

  @Override
  public @NotNull String getDescription(@NotNull PsiElement e) {
    return CodeInsightBundle.message("unwrap.with.placeholder", e.getText());
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e instanceof PsiTypeElement && e.getFirstChild() == e.getLastChild() && e.getParent() instanceof PsiReferenceParameterList;
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<? super PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return e.getParent().getParent();
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) {
    PsiElement parent = element.getParent().getParent();
    context.extractElement(element, parent);
    context.delete(parent);
  }
}
