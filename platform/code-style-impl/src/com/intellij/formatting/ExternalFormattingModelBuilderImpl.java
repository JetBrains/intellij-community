// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.ExternalFormatProcessor;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ExternalFormattingModelBuilderImpl implements FormattingModelBuilder {
  private final @Nullable FormattingModelBuilder myDefaultBuilder;

  public ExternalFormattingModelBuilderImpl(@Nullable FormattingModelBuilder defaultBuilder) {
    myDefaultBuilder = defaultBuilder;
  }

  @Override
  public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
    if (formattingContext.getFormattingMode() == FormattingMode.REFORMAT &&
        ExternalFormatProcessor.useExternalFormatter(formattingContext.getContainingFile()) || myDefaultBuilder == null) {
      return new DummyFormattingModel(formattingContext.getPsiElement());
    }
    return myDefaultBuilder.createModel(formattingContext);
  }


}
