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

package com.intellij.execution.util;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableCellEditor;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

public class EnvVariablesTable extends ListTableWithButtons<EnvironmentVariable> {
  public EnvVariablesTable() {
    getTableView().getEmptyText().setText("No variables");
  }
  
  @Override
  protected ListTableModel createListModel() {
    final ColumnInfo name = new ElementsColumnInfoBase<EnvironmentVariable>("Name") {
      @Override
      public String valueOf(EnvironmentVariable environmentVariable) {
        return environmentVariable.getName();
      }

      @Override
      public boolean isCellEditable(EnvironmentVariable environmentVariable) {
        return environmentVariable.getNameIsWriteable();
      }

      @Override
      public void setValue(EnvironmentVariable environmentVariable, String s) {
        if (s.equals(valueOf(environmentVariable))) {
          return;
        }
        environmentVariable.setName(s);
        setModified();
      }

      @Override
      protected String getDescription(EnvironmentVariable environmentVariable) {
        return environmentVariable.getDescription();
      }
    };

    final ColumnInfo value = new ElementsColumnInfoBase<EnvironmentVariable>("Value") {
      @Override
      public String valueOf(EnvironmentVariable environmentVariable) {
        return environmentVariable.getValue();
      }

      @Override
      public boolean isCellEditable(EnvironmentVariable environmentVariable) {
        return !environmentVariable.getIsPredefined();
      }

      @Override
      public void setValue(EnvironmentVariable environmentVariable, String s) {
        if (s.equals(valueOf(environmentVariable))) {
          return;
        }
        environmentVariable.setValue(s);
        setModified();
      }

      @Nullable
      @Override
      protected String getDescription(EnvironmentVariable environmentVariable) {
        return environmentVariable.getDescription();
      }

      @Nullable
      @Override
      public TableCellEditor getEditor(EnvironmentVariable variable) {
        StringWithNewLinesCellEditor editor = new StringWithNewLinesCellEditor();
        editor.setClickCountToStart(1);
        return editor;
      }
    };

    return new ListTableModel((new ColumnInfo[]{name, value}));
  }

  public void editVariableName(final EnvironmentVariable environmentVariable) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final EnvironmentVariable actualEnvVar = ContainerUtil.find(getElements(),
                                                                  item -> StringUtil.equals(environmentVariable.getName(), item.getName()));
      if (actualEnvVar == null) {
        return;
      }

      setSelection(actualEnvVar);
      if (actualEnvVar.getNameIsWriteable()) {
        editSelection(0);
      }
    });
  }

  public List<EnvironmentVariable> getEnvironmentVariables() {
    return getElements();
  }

  @Override
  protected EnvironmentVariable createElement() {
    return new EnvironmentVariable("", "", false);
  }

  @Override
  protected boolean isEmpty(EnvironmentVariable element) {
    return element.getName().isEmpty() && element.getValue().isEmpty();
  }

  @NotNull
  @Override
  protected AnActionButton[] createExtraActions() {
    AnActionButton copyButton = new AnActionButton(ActionsBundle.message("action.EditorCopy.text"), AllIcons.Actions.Copy) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        stopEditing();
        StringBuilder sb = new StringBuilder();
        List<EnvironmentVariable> variables = getSelection();
        for (EnvironmentVariable environmentVariable : variables) {
          if (environmentVariable.getIsPredefined() || isEmpty(environmentVariable)) continue;
          if (sb.length() > 0) sb.append('\n');
          sb.append(StringUtil.escapeChar(environmentVariable.getName(), '=')).append('=')
            .append(StringUtil.escapeChar(environmentVariable.getValue(), '='));
        }
        CopyPasteManager.getInstance().setContents(new StringSelection(sb.toString()));
      }

      @Override
      public boolean isEnabled() {
        return super.isEnabled() && !getSelection().isEmpty();
      }
    };
    AnActionButton pasteButton = new AnActionButton(ActionsBundle.message("action.EditorPaste.text"), AllIcons.Actions.Menu_paste) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        removeSelected();
        stopEditing();
        String content = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
        if (content == null || !content.contains("=")) return;
        List<EnvironmentVariable> parsed = new ArrayList<>();
        List<String> lines = StringUtil.split(content, "\n");
        for (String line : lines) {
          int pos = line.indexOf('=');
          if (pos == -1) continue;
          while (pos > 0 && line.charAt(pos - 1) == '\\') {
            pos = line.indexOf('=', pos + 1);
          }
          line = line.replaceAll("[\\\\]{1}","\\\\\\\\");
          parsed.add(new EnvironmentVariable(
            StringUtil.unescapeStringCharacters(line.substring(0, pos)),
            StringUtil.unescapeStringCharacters(line.substring(pos + 1)),
            false));
        }
        List<EnvironmentVariable> variables = new ArrayList<>(getEnvironmentVariables());
        variables.addAll(parsed);
        setValues(variables);
      }
    };
    return new AnActionButton[]{copyButton, pasteButton};
  }

  @Override
  protected EnvironmentVariable cloneElement(EnvironmentVariable envVariable) {
    return envVariable.clone();
  }

  @Override
  protected boolean canDeleteElement(EnvironmentVariable selection) {
    return !selection.getIsPredefined();
  }
}
