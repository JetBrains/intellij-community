// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis.encoding;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class EncodingReferenceInjector extends ReferenceInjector {
  @Override
  public PsiReference @NotNull [] getReferences(@NotNull PsiElement element, @NotNull ProcessingContext context, @NotNull TextRange range) {
    return new PsiReference[]{new EncodingReference(element, range.substring(element.getText()), range)};
  }

  @NotNull
  @Override
  public String getId() {
    return "encoding-reference";
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return CodeInsightBundle.message("label.encoding.name");
  }
}
