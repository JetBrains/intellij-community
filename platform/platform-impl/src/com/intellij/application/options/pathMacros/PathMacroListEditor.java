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
package com.intellij.application.options.pathMacros;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

/**
 * @author dsl
 */
public class PathMacroListEditor {
  JPanel myPanel;
  JButton myAddButton;
  JButton myRemoveButton;
  JButton myEditButton;
  JScrollPane myScrollPane;
  private PathMacroTable myPathMacroTable;

  public PathMacroListEditor() {
    this(null);
  }

  public PathMacroListEditor(final Collection<String> undefinedMacroNames) {
    myPathMacroTable = undefinedMacroNames != null ? new PathMacroTable(undefinedMacroNames) : new PathMacroTable();
    myScrollPane.setViewportView(myPathMacroTable);
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathMacroTable.addMacro();
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathMacroTable.removeSelectedMacros();
      }
    });
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathMacroTable.editMacro();
      }
    });
  }

  public void commit() throws ConfigurationException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myPathMacroTable.commit();
      }
    });
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public void reset() {
    myPathMacroTable.reset();
  }

  public boolean isModified() {
    return myPathMacroTable.isModified();
  }

  private void createUIComponents() {
  }
}
