// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.CollectionComboBoxModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public abstract class LafManager {
  public static LafManager getInstance() {
    return ApplicationManager.getApplication().getService(LafManager.class);
  }

  public abstract UIManager.LookAndFeelInfo @NotNull [] getInstalledLookAndFeels();

  @ApiStatus.Internal
  public abstract @NotNull CollectionComboBoxModel<LafReference> getLafComboBoxModel();

  @ApiStatus.Internal
  public abstract UIManager.LookAndFeelInfo findLaf(LafReference reference);

  public abstract UIManager.LookAndFeelInfo getCurrentLookAndFeel();

  @ApiStatus.Internal
  public abstract LafReference getLookAndFeelReference();

  @ApiStatus.Internal
  public abstract ListCellRenderer<LafReference> getLookAndFeelCellRenderer();

  @ApiStatus.Internal
  public abstract @NotNull JComponent getSettingsToolbar();

  public void setCurrentLookAndFeel(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo) {
    setCurrentLookAndFeel(lookAndFeelInfo, false);
  }

  public abstract void setCurrentLookAndFeel(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo, boolean lockEditorScheme);

  public abstract void updateUI();

  public abstract void repaintUI();

  public abstract boolean getAutodetect();

  public abstract void setAutodetect(boolean value);

  public abstract boolean getAutodetectSupported();

  public abstract void setPreferredDarkLaf(@NotNull UIManager.LookAndFeelInfo value);

  public abstract void setPreferredLightLaf(@NotNull UIManager.LookAndFeelInfo value);

  /**
   * @deprecated Use {@link LafManagerListener#TOPIC}
   */
  @Deprecated(forRemoval = true)
  public abstract void addLafManagerListener(@NotNull LafManagerListener listener);

  /**
   * @deprecated Use {@link LafManagerListener#TOPIC}
   */
  @Deprecated(forRemoval = true)
  public abstract void removeLafManagerListener(@NotNull LafManagerListener listener);

  public static final class LafReference {
    private final String name;
    private final String className;
    private final String themeId;

    public LafReference(@NotNull String name, @Nullable String className, @Nullable String themeId) {
      this.name = name;
      this.className = className;
      this.themeId = themeId;
    }

    @Override
    public @NlsSafe @NlsContexts.Label String toString() {
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
             Objects.equals(className, reference.className) &&
             Objects.equals(themeId, reference.themeId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, className, themeId);
    }
  }
}