// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInspection.jigsaw.JigsawUtil;
import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaModule;
import org.jetbrains.annotations.NotNull;

public class JigsawProviderLookupElement extends LookupElement {
  private final PsiClass myPsiClass;

  public JigsawProviderLookupElement(PsiClass aClass) { myPsiClass = aClass; }

  @Override
  public @NotNull String getLookupString() {
    return PsiJavaModule.PROVIDER_METHOD;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setTypeText(JavaBundle.message("completion.provider.method.declaration.type"));
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    context.getDocument().replaceString(context.getStartOffset(), context.getSelectionEndOffset(), "");
    JigsawUtil.addProviderMethod(myPsiClass, context.getEditor(), context.getStartOffset());
  }
}
