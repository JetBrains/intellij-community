// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionMacro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;

public final class ActionMacroConfigurable implements Configurable {
  private ActionMacroConfigurationPanel myPanel;

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.edit.macros");
  }

  @Override
  public JComponent createComponent() {
    myPanel = new ActionMacroConfigurationPanel();
    return myPanel.getPanel();
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myPanel);
    myPanel = null;
  }
}
