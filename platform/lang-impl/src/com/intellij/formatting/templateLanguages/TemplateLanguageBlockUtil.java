// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.templateLanguages;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TemplateLanguageBlockUtil extends AbstractBlockUtil<DataLanguageBlockWrapper, TemplateLanguageBlock> {
  public static final TemplateLanguageBlockUtil INSTANCE = new TemplateLanguageBlockUtil();

  private TemplateLanguageBlockUtil() {super();}

  @Nullable
  @Override
  protected DataLanguageBlockWrapper createBlockWrapper(@NotNull Block block, Indent indent) {
    return DataLanguageBlockWrapper.create(block);
  }

  @NotNull
  @Override
  protected Block createBlockFragmentWrapper(@NotNull Block block, @NotNull TextRange dataBlockTextRange) {
    return new DataLanguageBlockFragmentWrapper(block, dataBlockTextRange);
  }
}
