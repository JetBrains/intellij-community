// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInspection.jigsaw.JigsawApiConstants;
import com.intellij.codeInspection.jigsaw.JigsawUtil;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

public class JigsawProviderLookupElement extends LookupElement {
  private final PsiClass myPsiClass;

  public JigsawProviderLookupElement(PsiClass aClass) { myPsiClass = aClass; }

  @Override
  public @NotNull String getLookupString() {
    return JigsawApiConstants.PROVIDER;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    super.renderElement(presentation);
    presentation.setTypeText("provider() method declaration");
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    int selectionLength = context.getSelectionEndOffset() - context.getStartOffset();
    JigsawUtil.addProviderMethod(myPsiClass, context.getEditor(), context.getStartOffset(),
                                 (offset, content) -> context.getDocument().replaceString(offset, offset + selectionLength,
                                                                                          content));
  }
}
