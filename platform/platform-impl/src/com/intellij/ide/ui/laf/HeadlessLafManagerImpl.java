// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.BasicOptionButtonUI;
import com.intellij.ui.components.DefaultLinkButtonUI;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class HeadlessLafManagerImpl extends LafManager {
  public HeadlessLafManagerImpl() {
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
  public LafReference getCurrentLookAndFeelReference() {
    return null;
  }

  @Override
  public void setCurrentLookAndFeel(UIManager.@NotNull LookAndFeelInfo lookAndFeelInfo, boolean lockEditorScheme) { }

  @Override
  public CollectionComboBoxModel<LafReference> getLafComboBoxModel() {
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
  public void addLafManagerListener(@NotNull LafManagerListener listener) { }

  @Override
  public void addLafManagerListener(@NotNull LafManagerListener listener, @NotNull Disposable disposable) { }

  @Override
  public void removeLafManagerListener(@NotNull LafManagerListener listener) { }
}