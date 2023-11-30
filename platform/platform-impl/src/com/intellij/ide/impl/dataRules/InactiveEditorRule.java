// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class InactiveEditorRule implements GetDataRule {
  @Override
  public @Nullable Object getData(final @NotNull DataProvider dataProvider) {
    return dataProvider.getData(CommonDataKeys.EDITOR.getName());
  }
}