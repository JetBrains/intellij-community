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
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.text.StringTokenizer;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author dsl
 */
public class PathMacroListEditor {
  JPanel myPanel;
  private JTextField myIgnoredVariables;
  private JPanel myPathVariablesPanel;
  private PathMacroTable myPathMacroTable;

  public PathMacroListEditor() {
    this(null);
  }

  public PathMacroListEditor(final Collection<String> undefinedMacroNames) {
    myPathMacroTable = undefinedMacroNames != null ? new PathMacroTable(undefinedMacroNames) : new PathMacroTable();
    myPathVariablesPanel.add(
      ToolbarDecorator.createDecorator(myPathMacroTable)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            myPathMacroTable.addMacro();
          }
        }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          myPathMacroTable.removeSelectedMacros();
        }
      }).setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          myPathMacroTable.editMacro();
        }
      }).disableUpDownActions().createPanel(), BorderLayout.CENTER);

    fillIgnoredVariables();
  }

  private void fillIgnoredVariables() {
    final Collection<String> ignored = PathMacros.getInstance().getIgnoredMacroNames();
    myIgnoredVariables.setText(StringUtil.join(ignored, ";"));
  }

  private boolean isIgnoredModified() {
    final Collection<String> ignored = PathMacros.getInstance().getIgnoredMacroNames();
    return !parseIgnoredVariables().equals(ignored);
  }

  private Collection<String> parseIgnoredVariables() {
    final String s = myIgnoredVariables.getText();
    final List<String> ignored = new ArrayList<>();
    final StringTokenizer st = new StringTokenizer(s, ";");
    while (st.hasMoreElements()) {
      ignored.add(st.nextElement().trim());
    }

    return ignored;
  }

  public void commit() throws ConfigurationException {
    ApplicationManager.getApplication().runWriteAction(() -> {
      myPathMacroTable.commit();

      final Collection<String> ignored = parseIgnoredVariables();
      final PathMacros instance = PathMacros.getInstance();
      instance.setIgnoredMacroNames(ignored);
    });
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public void reset() {
    myPathMacroTable.reset();
    fillIgnoredVariables();
  }

  public boolean isModified() {
    return myPathMacroTable.isModified() || isIgnoredModified();
  }

  private void createUIComponents() {
  }
}
