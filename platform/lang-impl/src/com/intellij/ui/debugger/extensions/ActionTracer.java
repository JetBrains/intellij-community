/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.debugger.extensions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.debugger.UiDebuggerExtension;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: kirillk
 * Date: 8/4/11
 * Time: 7:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class ActionTracer implements UiDebuggerExtension, AnActionListener {

  private final Logger LOG = Logger.getInstance("ActionTracer");
  
  private JTextArea myText;
  private JPanel myComponent;

  @Override
  public JComponent getComponent() {
    if (myComponent == null) {
      myText = new JTextArea();
      final JBScrollPane log = new JBScrollPane(myText);
      final AnAction clear = new AnAction("Clear", "Clear log", AllIcons.General.Reset) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myText.setText(null);
        }
      };
      myComponent = new JPanel(new BorderLayout());
      final DefaultActionGroup group = new DefaultActionGroup();
      group.add(clear);
      myComponent.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent(), BorderLayout.NORTH);
      myComponent.add(log);

      ActionManager.getInstance().addAnActionListener(this);
    }

    return myComponent;
  }

  @Override
  public String getName() {
    return "Actions";
  }

  @Override
  public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
  }

  @Override
  public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
    StringBuilder out = new StringBuilder();
    final ActionManager actionManager = ActionManager.getInstance();
    final String id = actionManager.getId(action);
    out.append("id=").append(id);
    if (id != null) {
      out.append(" shortcuts:");
      final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(id);
      for (int i = 0; i < shortcuts.length; i++) {
        Shortcut shortcut = shortcuts[i];
        out.append(shortcut);
        if (i < shortcuts.length - 1) {
          out.append(",");
        }
      }
    }
    out.append("\n");
    final Document doc = myText.getDocument();
    try {
      doc.insertString(doc.getLength(), out.toString(), null);
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          final int y = (int)myText.getBounds().getMaxY();
          myText.scrollRectToVisible(new Rectangle(0, y, myText.getBounds().width, 0));
        }
      });
    }
    catch (BadLocationException e) {
      LOG.error(e);
    }
  }

  @Override
  public void beforeEditorTyping(char c, DataContext dataContext) {
  }

  @Override
  public void disposeUiResources() {
    ActionManager.getInstance().removeAnActionListener(this);
    myComponent = null;
    myText = null;
    
  }
}
