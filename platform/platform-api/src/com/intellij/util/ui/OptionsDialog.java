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
package com.intellij.util.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */

public abstract class OptionsDialog extends DialogWrapper  {

  protected final Project myProject;

  private class MyDoNotAsk implements DoNotAskOption {
    public boolean isToBeShown() {
      return OptionsDialog.this.isToBeShown();
    }

    public void setToBeShown(boolean value, boolean onOk) {
      OptionsDialog.this.setToBeShown(value, onOk);
    }

    public boolean canBeHidden() {
      return OptionsDialog.this.canBeHidden();
    }

    public boolean shouldSaveOptionsOnCancel() {
      return OptionsDialog.this.shouldSaveOptionsOnCancel();
    }
  }

  protected OptionsDialog(Project project) {
    super(project, true);
    myProject = project;
    setDoNotAskOption(new MyDoNotAsk());
  }

  protected OptionsDialog(Project project, boolean canBeParent) {
    super(project, canBeParent);
    myProject = project;
    setDoNotAskOption(new MyDoNotAsk());
  }

  protected OptionsDialog(boolean canBeParent) {
    super(canBeParent);
    myProject = null;
    setDoNotAskOption(new MyDoNotAsk());
  }

  protected OptionsDialog(Component parent, boolean canBeParent) {
    super(parent, canBeParent);
    myProject = null;
    setDoNotAskOption(new MyDoNotAsk());
  }

  public static JPanel addDoNotShowCheckBox(JComponent southPanel, JCheckBox checkBox) {
    return DialogWrapper.addDoNotShowCheckBox(southPanel, checkBox);
  }

  public static boolean shiftIsPressed(int inputEventModifiers) {
    return (inputEventModifiers & Event.SHIFT_MASK) != 0;
  }


  protected abstract boolean isToBeShown();

  protected abstract void setToBeShown(boolean value, boolean onOk);

  protected boolean canBeHidden() {
    return true;
  }

  protected abstract boolean shouldSaveOptionsOnCancel();
}
