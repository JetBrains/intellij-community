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
package com.intellij.debugger.ui.breakpoints.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.ui.breakpoints.BreakpointPanel;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2005
 */
public class SwitchViewAction extends BreakpointPanelAction {
  public SwitchViewAction() {
    super(DebuggerBundle.message("button.switch.view"));
  }


  public void actionPerformed(ActionEvent e) {
    getPanel().switchViews();
  }


  public void update() {
    final AbstractButton button = getButton();
    final BreakpointPanel panel = getPanel();
    button.setText(panel.isTreeShowing()? DebuggerBundle.message("button.list.view") : DebuggerBundle.message("button.tree.view"));
    button.setEnabled(panel.getBreakpointCount() > 0);
  }
}
