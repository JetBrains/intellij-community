/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
class ComboBoxVisibilityGroup extends ComboBoxAction {
  private String myValue;
  private DefaultActionGroup myGroup;

  protected ComboBoxVisibilityGroup(final String[] options, String[] presentableNames, final Runnable run) {
    final AnAction[] myActions = new AnAction[options.length];
    for (int i = 0; i < options.length; i++) {
      final String value = options[i];
      myActions[i] = new AnAction(presentableNames[i]) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          setValue(value);
          run.run();
        }
      };
    }
    myGroup = new DefaultActionGroup(myActions);
  }

  public String getValue() {
    return myValue;
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    return myGroup;
  }

  public void setValue(String value) {
    getTemplatePresentation().setText(value);
    myValue = value;
  }
}
