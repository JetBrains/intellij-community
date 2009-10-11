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

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2005
 */
public abstract class BreakpointPanelAction implements ActionListener {
  private final String myName;
  private AbstractButton myButton;
  private BreakpointPanel myPanel;

  protected BreakpointPanelAction(String name) {
    myName = name;
  }

  public final String getName() {
    return myName;
  }

  public boolean isStateAction() {
    return false;
  }

  public void setPanel(BreakpointPanel panel) {
    myPanel = panel;
  }

  public final BreakpointPanel getPanel() {
    return myPanel;
  }

  public void setButton(AbstractButton button) {
    myButton = button;
  }

  public final AbstractButton getButton() {
    return myButton;
  }

  public abstract void update();
}
