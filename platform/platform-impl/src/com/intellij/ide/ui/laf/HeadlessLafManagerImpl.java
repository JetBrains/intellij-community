// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.ui.CollectionComboBoxModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class HeadlessLafManagerImpl extends LafManager {
  public HeadlessLafManagerImpl() {
    LafManagerImpl.fixOptionButton(UIManager.getLookAndFeelDefaults());
  }

  @NotNull
  @Override
  public UIManager.LookAndFeelInfo[] getInstalledLookAndFeels() {
    return new UIManager.LookAndFeelInfo[0];
  }

  @Override
  public UIManager.LookAndFeelInfo getCurrentLookAndFeel() {
    return null;
  }

  @Override
  public CollectionComboBoxModel<UIManager.LookAndFeelInfo> getLafComboBoxModel() {
    return new CollectionComboBoxModel<>();
  }

  @Override
  public void setCurrentLookAndFeel(@NotNull UIManager.LookAndFeelInfo lookAndFeelInfo) { }

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