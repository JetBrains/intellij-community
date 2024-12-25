// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public abstract class CodeFormattingDataPreparer {

  public static final ExtensionPointName<CodeFormattingDataPreparer>
    EP_NAME = ExtensionPointName.create("com.intellij.codeFormattingDataPreparer");

  public abstract void prepareFormattingData(@NotNull PsiFile file, @NotNull List<TextRange> ranges, @NotNull CodeFormattingData data);
}