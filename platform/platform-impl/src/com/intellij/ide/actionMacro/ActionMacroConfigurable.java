/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actionMacro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;

public class ActionMacroConfigurable implements Configurable {
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
