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

import com.intellij.debugger.ui.breakpoints.BreakpointPanel;
import com.intellij.debugger.DebuggerBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2005
 */
public class ToggleGroupByClassesAction extends BreakpointPanelAction {
  public ToggleGroupByClassesAction() {
    super(DebuggerBundle.message("button.group.by.classes"));
  }

  public boolean isStateAction() {
    return true;
  }

  public void setButton(AbstractButton button) {
    super.setButton(button);
    getButton().setSelected(getPanel().getTree().isGroupByClasses());
  }

  public void actionPerformed(ActionEvent e) {
    getPanel().getTree().setGroupByClasses(getButton().isSelected());
  }

  public void update() {
    final AbstractButton button = getButton();
    final BreakpointPanel panel = getPanel();
    button.setEnabled(panel.getBreakpointCount() > 0 && panel.isTreeShowing());
    button.setSelected(panel.getTree().isGroupByClasses());
  }
}
