// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

abstract class LightEditStatusBarBase extends JPanel implements StatusBar {
  // region Deprecated methods to be removed
  @Override
  public final void addWidget(@NotNull StatusBarWidget widget) {}

  @Override
  public final void addWidget(@NotNull StatusBarWidget widget, @NotNull String anchor) {}

  @Override
  public final void addWidget(@NotNull StatusBarWidget widget, @NotNull Disposable parentDisposable) {}

  @Override
  public final void addWidget(@NotNull StatusBarWidget widget, @NotNull String anchor, @NotNull Disposable parentDisposable) {}

  @Override
  public final void addCustomIndicationComponent(@NotNull JComponent c) {}

  @Override
  public final void removeCustomIndicationComponent(@NotNull JComponent c) {}

  @Override
  public final void removeWidget(@NotNull String id) {}

  // endregion

  // region Unsupported operations and dummy methods

  @Nullable
  @Override
  public StatusBar createChild(@NotNull IdeFrame frame) {
    throw  new UnsupportedOperationException();
  }


  @Override
  public StatusBar findChild(Component c) {
    return null;
  }


  @Nullable
  @Override
  public IdeFrame getFrame() {
    return null;
  }

  // endregion

}
