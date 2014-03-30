/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.application.ApplicationBundle;

import javax.swing.*;
import java.awt.*;

public class JavaErrorOptionsProvider implements ErrorOptionsProvider {
  private JCheckBox mySuppressWay;

  @Override
  public JComponent createComponent() {
    mySuppressWay = new JCheckBox(ApplicationBundle.message("checkbox.suppress.with.suppresswarnings"));
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(mySuppressWay, BorderLayout.EAST);
    return panel;
  }

  @Override
  public void reset() {
    mySuppressWay.setSelected(DaemonCodeAnalyzerSettings.getInstance().isSuppressWarnings());
  }

  @Override
  public void disposeUIResources() {
    mySuppressWay = null;
  }

  @Override
  public void apply() {
    DaemonCodeAnalyzerSettings.getInstance().setSuppressWarnings(mySuppressWay.isSelected());
  }

  @Override
  public boolean isModified() {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    return mySuppressWay.isSelected() != settings.isSuppressWarnings();
  }

}