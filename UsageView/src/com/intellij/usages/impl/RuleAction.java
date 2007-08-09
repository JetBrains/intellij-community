/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages.impl;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 19, 2005
 */
abstract class RuleAction extends ToggleAction {
  private UsageViewImpl myView;
  private boolean myState;

  public RuleAction(UsageViewImpl view, final String text, final Icon icon) {
    super(text, null, icon);
    myView = view;
    myState = getOptionValue();
  }

  protected abstract boolean getOptionValue();

  protected abstract void setOptionValue(boolean value);

  public boolean isSelected(AnActionEvent e) {
    return myState;
  }

  public void setSelected(AnActionEvent e, boolean state) {
    setOptionValue(state);
    myState = state;
    myView.rulesChanged();
    myView.select();
  }
}
