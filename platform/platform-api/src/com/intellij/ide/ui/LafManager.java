// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
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

  /**
   * @deprecated Do not use.
   */
  @Deprecated
  public abstract UIManager.LookAndFeelInfo @NotNull [] getInstalledLookAndFeels();

  @ApiStatus.Internal
  public abstract @NotNull CollectionComboBoxModel<LafReference> getLafComboBoxModel();

  @ApiStatus.Internal
  public abstract UIThemeLookAndFeelInfo findLaf(LafReference reference);

  /**
   * @deprecated Use {@link LafManager#getCurrentUIThemeLookAndFeel()}
   */
  @Deprecated(forRemoval = true)
  public abstract UIManager.LookAndFeelInfo getCurrentLookAndFeel();

  public abstract UIThemeLookAndFeelInfo getCurrentUIThemeLookAndFeel();

  @ApiStatus.Internal
  public abstract LafReference getLookAndFeelReference();

  @ApiStatus.Internal
  public abstract ListCellRenderer<LafReference> getLookAndFeelCellRenderer();

  @ApiStatus.Internal
  public abstract @NotNull JComponent getSettingsToolbar();

  /**
   * @deprecated Use {@link LafManager#setCurrentUIThemeLookAndFeel(UIThemeLookAndFeelInfo)}
   */
  @Deprecated(forRemoval = true)
  public void setCurrentLookAndFeel(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo) {
    setCurrentLookAndFeel((UIThemeLookAndFeelInfo)lookAndFeelInfo, false);
  }

  public void setCurrentUIThemeLookAndFeel(@NotNull UIThemeLookAndFeelInfo lookAndFeelInfo) {
    setCurrentLookAndFeel(lookAndFeelInfo, false);
  }

  public abstract void setCurrentLookAndFeel(@NotNull UIThemeLookAndFeelInfo lookAndFeelInfo, boolean lockEditorScheme);

  public abstract void updateUI();

  public abstract void repaintUI();

  /**
   * @return if autodetect is supported and enabled
   */
  public abstract boolean getAutodetect();

  public abstract void setAutodetect(boolean value);

  public abstract boolean getAutodetectSupported();

  public abstract void setPreferredDarkLaf(@NotNull UIThemeLookAndFeelInfo value);

  public abstract void setPreferredLightLaf(@NotNull UIThemeLookAndFeelInfo value);

  @ApiStatus.Internal
  public abstract void setRememberSchemeForLaf(boolean rememberSchemeForLaf);

  @ApiStatus.Internal
  public abstract void rememberSchemeForLaf(@NotNull EditorColorsScheme scheme);

  @ApiStatus.Internal
  public void applyDensity() { }

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
    private final String themeId;

    public LafReference(@NotNull String name, @Nullable String themeId) {
      this.name = name;
      this.themeId = themeId;
    }

    @Override
    public @NlsSafe @NlsContexts.Label String toString() {
      return name;
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
             Objects.equals(themeId, reference.themeId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, themeId);
    }

  }

  public abstract @Nullable UIThemeLookAndFeelInfo getDefaultLightLaf();

  public abstract @Nullable UIThemeLookAndFeelInfo getDefaultDarkLaf();
}