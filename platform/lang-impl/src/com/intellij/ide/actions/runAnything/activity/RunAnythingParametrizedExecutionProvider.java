// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface RunAnythingParametrizedExecutionProvider<V> extends RunAnythingActivityProvider {
  @Nullable
  default String getTextAsParameter(@NotNull V value) {
    return null;
  }

  @Nullable
  V findMatchingValue(@NotNull DataContext dataContext, @NotNull String pattern);

  void execute(@NotNull DataContext dataContext, @NotNull V value);

  @Nullable
  default Icon getIcon(@NotNull V value) {
    return EmptyIcon.ICON_16;
  }

  default boolean isMatching(@NotNull DataContext dataContext, @NotNull String pattern, @NotNull V value) {
    return StringUtil.equals(pattern, getFullCommand(value));
  }

  @Nullable
  default String getCommandPrefix() {
    return null;
  }

  @NotNull
  default String getFullCommand(@NotNull V value) {
    String command = getCommandPrefix();
    String text = getTextAsParameter(value);
    return command != null && text != null ? command + " " + text
                                           : command != null ? command
                                                             : text != null ? text : "";
  }
}
