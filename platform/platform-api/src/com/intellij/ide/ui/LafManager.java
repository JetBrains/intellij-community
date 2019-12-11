// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.CollectionComboBoxModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class LafManager {
  public static LafManager getInstance() {
    return ApplicationManager.getApplication().getComponent(LafManager.class);
  }

  @NotNull
  public abstract UIManager.LookAndFeelInfo[] getInstalledLookAndFeels();

  @ApiStatus.Internal
  public abstract CollectionComboBoxModel<UIManager.LookAndFeelInfo> getLafComboBoxModel();

  @Nullable
  public abstract UIManager.LookAndFeelInfo getCurrentLookAndFeel();

  public abstract void setCurrentLookAndFeel(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo);

  public abstract void updateUI();

  public abstract void repaintUI();

  /**
   * @deprecated Use {@link LafManagerListener#TOPIC}
   */
  @Deprecated
  public abstract void addLafManagerListener(@NotNull LafManagerListener listener);

  /**
   * @deprecated Use {@link LafManagerListener#TOPIC}
   */
  @Deprecated
  public abstract void addLafManagerListener(@NotNull LafManagerListener listener, @NotNull Disposable disposable);

  /**
   * @deprecated Use {@link LafManagerListener#TOPIC}
   */
  @Deprecated
  public abstract void removeLafManagerListener(@NotNull LafManagerListener listener);
}