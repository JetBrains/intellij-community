// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.CollectionComboBoxModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public abstract class LafManager {
  public static LafManager getInstance() {
    return ApplicationManager.getApplication().getComponent(LafManager.class);
  }

  public abstract UIManager.LookAndFeelInfo @NotNull [] getInstalledLookAndFeels();

  @ApiStatus.Internal
  public abstract CollectionComboBoxModel<LafReference> getLafComboBoxModel();

  @ApiStatus.Internal
  public abstract UIManager.LookAndFeelInfo findLaf(LafReference reference);

  @Nullable
  public abstract UIManager.LookAndFeelInfo getCurrentLookAndFeel();

  @ApiStatus.Internal
  public abstract LafReference getCurrentLookAndFeelReference();

  public void setCurrentLookAndFeel(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo) {
    setCurrentLookAndFeel(lookAndFeelInfo, false);
  }

  public abstract void setCurrentLookAndFeel(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo, boolean lockEditorScheme);

  public abstract void updateUI();

  public abstract void repaintUI();

  public abstract boolean isAutoDetect();

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

  public static class LafReference {
    private final String name;
    private final String className;
    private final String themeId;

    public LafReference(@NotNull String name, @NotNull String className, @Nullable String themeId) {
      this.name = name;
      this.className = className;
      this.themeId = themeId;
    }

    @Override
    public String toString() {
      return name;
    }

    public String getClassName() {
      return className;
    }

    public String getThemeId() {
      return themeId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      LafReference reference = (LafReference)o;
      return name.equals(reference.name) &&
             className.equals(reference.className) &&
             Objects.equals(themeId, reference.themeId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, className, themeId);
    }
  }
}