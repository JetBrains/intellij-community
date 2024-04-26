// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.psi.codeStyle.ExternalFormatProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExternalFormattingModelBuilderImpl implements FormattingModelBuilder {
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
