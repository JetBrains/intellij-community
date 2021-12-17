// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Formatter extends IndentFactory, WrapFactory, AlignmentFactory, SpacingFactory, FormattingModelFactory {
  static Formatter getInstance() {
    Formatter instance = Holder.INSTANCE;
    if (instance == null) {
      instance = ApplicationManager.getApplication().getService(Formatter.class);
      Holder.INSTANCE = instance;
    }
    return instance;
  }

  @ApiStatus.Internal
  @Nullable
  FormattingModelBuilder createExternalFormattingModelBuilder(@NotNull PsiFile psiFile, @Nullable FormattingModelBuilder langBuilder);

  @ApiStatus.Internal
  boolean isEligibleForVirtualFormatting(@NotNull PsiElement context);

  @ApiStatus.Internal
  @Nullable
  FormattingModelBuilder wrapForVirtualFormatting(@NotNull PsiElement context, @Nullable FormattingModelBuilder originalModel);

}

final class Holder {
  // NotNullLazyValue is not used here because ServiceManager.getService can return null and better to avoid any possible issues here
  volatile static Formatter INSTANCE;
}
