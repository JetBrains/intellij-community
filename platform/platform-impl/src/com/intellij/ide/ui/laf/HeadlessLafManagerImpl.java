// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.BasicOptionButtonUI;
import com.intellij.ui.components.DefaultLinkButtonUI;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class HeadlessLafManagerImpl extends LafManager {
  HeadlessLafManagerImpl() {
    UIDefaults defaults = UIManager.getLookAndFeelDefaults();
    defaults.put("OptionButtonUI", BasicOptionButtonUI.class.getCanonicalName());
    defaults.put("LinkButtonUI", DefaultLinkButtonUI.class.getName());
    defaults.put("TreeUI", DefaultTreeUI.class.getName());
  }

  @Override
  public UIManager.LookAndFeelInfo @NotNull [] getInstalledLookAndFeels() {
    return new UIManager.LookAndFeelInfo[0];
  }

  @Override
  public UIManager.LookAndFeelInfo getCurrentLookAndFeel() {
    return null;
  }

  @Override
  public LafReference getLookAndFeelReference() {
    return null;
  }

  @Override
  public ListCellRenderer<LafReference> getLookAndFeelCellRenderer() {
    return null;
  }

  @Override
  @NotNull
  public JComponent getSettingsToolbar() {
    return new JComponent() {};
  }

  @Override
  public void setCurrentLookAndFeel(UIManager.@NotNull LookAndFeelInfo lookAndFeelInfo, boolean lockEditorScheme) { }

  @Override
  public @NotNull CollectionComboBoxModel<LafReference> getLafComboBoxModel() {
    return new CollectionComboBoxModel<>();
  }

  @Override
  public UIManager.LookAndFeelInfo findLaf(LafReference reference) {
    return null;
  }

  @Override
  public void updateUI() { }

  @Override
  public void repaintUI() { }

  @Override
  public boolean getAutodetect() {
    return false;
  }

  @Override
  public void setAutodetect(boolean value) {}

  @Override
  public boolean getAutodetectSupported() {
    return false;
  }

  @Override
  public void setPreferredDarkLaf(UIManager.@NotNull LookAndFeelInfo value) { }

  @Override
  public void setPreferredLightLaf(UIManager.@NotNull LookAndFeelInfo value) { }

  @Override
  public void addLafManagerListener(@NotNull LafManagerListener listener) { }

  @Override
  public void removeLafManagerListener(@NotNull LafManagerListener listener) { }
}