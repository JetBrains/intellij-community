// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

@ApiStatus.Internal
public final class ExecutionUiServiceImpl extends ExecutionUiService {
  @Override
  public @Nullable RunContentDescriptor showRunContent(@NotNull ExecutionResult executionResult,
                                                       @NotNull ExecutionEnvironment environment) {
    return new RunContentBuilder(executionResult, environment).showRunContent(environment.getContentToReuse());
  }

  @Override
  public @Nullable <S> SettingsEditor<S> createSettingsEditorFragmentWrapper(String id,
                                                                            @Nls String name,
                                                                            @Nls String group,
                                                                            @NotNull SettingsEditor<S> inner,
                                                                            Predicate<? super S> initialSelection) {
    return SettingsEditorFragment.createWrapper(id, name, group, inner, initialSelection);
  }
}
