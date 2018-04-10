/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

import static java.awt.GridBagConstraints.*;

class JavaHotSwapConfigurableUi implements ConfigurableUi<DebuggerSettings> {
  private JCheckBox myHotswapInBackground;
  private JCheckBox myCbCompileBeforeHotswap;
  private JCheckBox myCbHangWarningEnabled;
  private JRadioButton myRbAlways;
  private JRadioButton myRbNever;
  private JRadioButton myRbAsk;

  @Override
  public void reset(@NotNull DebuggerSettings settings) {
    myHotswapInBackground.setSelected(settings.HOTSWAP_IN_BACKGROUND);
    myCbCompileBeforeHotswap.setSelected(settings.COMPILE_BEFORE_HOTSWAP);
    myCbHangWarningEnabled.setSelected(settings.HOTSWAP_HANG_WARNING_ENABLED);

    if(DebuggerSettings.RUN_HOTSWAP_ALWAYS.equals(settings.RUN_HOTSWAP_AFTER_COMPILE)) {
      myRbAlways.setSelected(true);
    }
    else if(DebuggerSettings.RUN_HOTSWAP_NEVER.equals(settings.RUN_HOTSWAP_AFTER_COMPILE)) {
      myRbNever.setSelected(true);
    }
    else {
      myRbAsk.setSelected(true);
    }
  }

  @Override
  public void apply(@NotNull DebuggerSettings settings) {
    getSettingsTo(settings);
  }

  private void getSettingsTo(DebuggerSettings settings) {
    settings.HOTSWAP_IN_BACKGROUND = myHotswapInBackground.isSelected();
    settings.COMPILE_BEFORE_HOTSWAP = myCbCompileBeforeHotswap.isSelected();
    settings.HOTSWAP_HANG_WARNING_ENABLED = myCbHangWarningEnabled.isSelected();

    if (myRbAlways.isSelected()) {
      settings.RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ALWAYS;
    }
    else if (myRbNever.isSelected()) {
      settings.RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_NEVER;
    }
    else {
      settings.RUN_HOTSWAP_AFTER_COMPILE = DebuggerSettings.RUN_HOTSWAP_ASK;
    }
  }

  @Override
  public boolean isModified(@NotNull DebuggerSettings currentSettings) {
    final DebuggerSettings debuggerSettings = currentSettings.clone();
    getSettingsTo(debuggerSettings);
    return !debuggerSettings.equals(currentSettings);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    final JPanel panel = new JPanel(new GridBagLayout());

    myCbCompileBeforeHotswap = new JCheckBox(DebuggerBundle.message("label.debugger.hotswap.configurable.compile.before.hotswap"));
    myCbHangWarningEnabled = new JCheckBox(DebuggerBundle.message("label.debugger.hotswap.configurable.enable.vm.hang.warning"));
    myHotswapInBackground = new JCheckBox(DebuggerBundle.message("label.debugger.hotswap.configurable.hotswap.background"));
    myRbAlways = new JRadioButton(DebuggerBundle.message("label.debugger.hotswap.configurable.always"));
    myRbNever = new JRadioButton(DebuggerBundle.message("label.debugger.hotswap.configurable.never"));
    myRbAsk = new JRadioButton(DebuggerBundle.message("label.debugger.hotswap.configurable.ask"));

    panel.add(myCbCompileBeforeHotswap, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, NORTHWEST, NONE, JBUI.emptyInsets(), 0, 0));
    panel.add(myCbHangWarningEnabled, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, NORTHWEST, NONE, JBUI.insetsTop(4), 0, 0));
    panel.add(myHotswapInBackground, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 0.0, NORTHWEST, NONE, JBUI.insetsTop(4), 0, 0));
    
    int cbLeftOffset = 0;
    final Border border = myCbCompileBeforeHotswap.getBorder();
    if (border != null) {
      final Insets insets = border.getBorderInsets(myCbCompileBeforeHotswap);
      if (insets != null) {
        cbLeftOffset = insets.left;
      }
    }

    final ButtonGroup group = new ButtonGroup();
    group.add(myRbAlways);
    group.add(myRbNever);
    group.add(myRbAsk);
    final Box box = Box.createHorizontalBox();
    box.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)));
    box.add(myRbAlways);
    box.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)));
    box.add(myRbNever);
    box.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)));
    box.add(myRbAsk);
    final JPanel reloadPanel = new JPanel(new BorderLayout());
    reloadPanel.add(box, BorderLayout.CENTER);
    reloadPanel.add(new JLabel(DebuggerBundle.message("label.debugger.hotswap.configurable.reload.classes")), BorderLayout.WEST);
    panel.add(reloadPanel, new GridBagConstraints(0, RELATIVE, 1, 1, 1.0, 1.0, NORTHWEST, NONE, JBUI.insets(4, cbLeftOffset, 0, 0), 0, 0));

    return panel;
  }
}