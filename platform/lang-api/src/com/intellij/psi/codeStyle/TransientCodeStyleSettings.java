// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TransientCodeStyleSettings extends CodeStyleSettings {
  private final PsiFile myPsiFile;
  private final @NotNull CodeStyleSettings myOriginalSettings;
  private CodeStyleSource mySource;

  private TransientCodeStyleSettings(@NotNull PsiFile psiFile, @NotNull CodeStyleSettings settings) {
    myPsiFile = psiFile;
    myOriginalSettings = settings;
  }

  @Override
  public final boolean areTransient() {
    return true;
  }

  public static TransientCodeStyleSettings createFrom(@NotNull PsiFile file, @NotNull CodeStyleSettings settings) {
    TransientCodeStyleSettings transientSettings = new TransientCodeStyleSettings(file, settings);
    transientSettings.copyFrom(settings);
    return transientSettings;
  }

  public void setSource(@NotNull CodeStyleSource source) {
    mySource = source;
  }

  @Nullable
  public CodeStyleSource getSource() {
    return mySource;
  }

  @NotNull
  public PsiFile getPsiFile() {
    return myPsiFile;
  }

  @NotNull
  @Override
  public IndentOptions getIndentOptionsByFile(@Nullable PsiFile file,
                                              @Nullable TextRange formatRange,
                                              boolean ignoreDocOptions,
                                              @Nullable Processor<? super FileIndentOptionsProvider> providerProcessor) {
    if (file != null && file.isValid()) {
      FileType fileType = file.getFileType();
      return getIndentOptions(fileType);
    }
    return OTHER_INDENT_OPTIONS;
  }
}
