package com.intellij.execution.ui;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SettingsEditor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

@ApiStatus.Experimental
public class ExecutionUiService {

  public RunContentDescriptor showRunContent(@NotNull ExecutionResult executionResult,
                                             @NotNull ExecutionEnvironment environment) {
    return null;
  }

  public @Nullable <S> SettingsEditor<S> createSettingsEditorFragmentWrapper(String id,
                                                                            @Nls String name,
                                                                            @Nls String group,
                                                                            @NotNull SettingsEditor<S> inner,
                                                                            Predicate<? super S> initialSelection) {
    return null;
  }

  public static ExecutionUiService getInstance() {
    return ApplicationManager.getApplication().getService(ExecutionUiService.class);
  }
}
