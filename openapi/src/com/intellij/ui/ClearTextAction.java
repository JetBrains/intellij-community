/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 01-Sep-2006
 * Time: 21:17:18
 */
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;

public class ClearTextAction extends AnAction {

  public ClearTextAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final JComponent component = (JComponent)e.getDataContext().getData(DataConstants.CONTEXT_COMPONENT);
    if (component instanceof JTextComponent) {
      final JTextComponent textComponent = (JTextComponent)component;
      textComponent.setText("");
    }
  }


  public void update(AnActionEvent e) {
    final Component component = (Component)e.getDataContext().getData(DataConstants.CONTEXT_COMPONENT);
    e.getPresentation().setEnabled(component instanceof JTextComponent && ((JTextComponent)component).getText().length() > 0 && ((JTextComponent)component).isEditable());
  }
}