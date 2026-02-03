// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Consumer;

@ApiStatus.Internal
public interface WelcomeScreenLeftPanel {
  void addRootTab(@NotNull WelcomeScreenTab tab);
  void addSelectionListener(@NotNull Disposable disposable, @NotNull Consumer<? super WelcomeScreenTab> action);
  boolean selectTab(@NotNull WelcomeScreenTab tab);
  @Nullable WelcomeScreenTab getTabByIndex(int idx);
  void removeAllTabs();
  void init();
  @NotNull JComponent getComponent();
}