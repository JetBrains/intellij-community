// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util;

import com.intellij.execution.ExecutionBundle;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.util.List;

public class EnvVariablesTable extends ListTableWithButtons<EnvironmentVariable> {
  private CopyPasteProviderPanel myPanel;
  private boolean myPasteEnabled = false;

  public EnvVariablesTable() {
    getTableView().getEmptyText().setText(ExecutionBundle.message("empty.text.no.variables"));
    AnAction copyAction = ActionManager.getInstance().getAction(IdeActions.ACTION_COPY);
    if (copyAction != null) {
      copyAction.registerCustomShortcutSet(copyAction.getShortcutSet(), getTableView()); // no need to add in popup menu
    }
    AnAction pasteAction = ActionManager.getInstance().getAction(IdeActions.ACTION_PASTE);
    if (pasteAction != null) {
      pasteAction.registerCustomShortcutSet(pasteAction.getShortcutSet(), getTableView()); // no need to add in popup menu
    }
  }

  public void setPasteActionEnabled(boolean enabled) {
    myPasteEnabled = enabled;
  }

  @Override
  protected ListTableModel<EnvironmentVariable> createListModel() {
    return new ListTableModel<>(new NameColumnInfo(), new ValueColumnInfo());
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

  @Override
  public void setValues(@Unmodifiable List<? extends EnvironmentVariable> list) {
    super.setValues(ContainerUtil.sorted(list, Comparator.comparing(EnvironmentVariable::getName, NaturalComparator.INSTANCE)));
  }

  public List<EnvironmentVariable> getEnvironmentVariables() {
    return getElements();
  }

  @Override
  public JComponent getComponent() {
    if (myPanel == null) {
      myPanel = new CopyPasteProviderPanel(super.getComponent());
    }
    return myPanel;
  }

  @Override
  protected EnvironmentVariable createElement() {
    return new EnvironmentVariable("", "", false);
  }

  @Override
  protected boolean isEmpty(EnvironmentVariable element) {
    return element.getName().isEmpty() && element.getValue().isEmpty();
  }


  @Override
  protected EnvironmentVariable cloneElement(EnvironmentVariable envVariable) {
    return envVariable.clone();
  }

  @Override
  protected boolean canDeleteElement(EnvironmentVariable selection) {
    return !selection.getIsPredefined();
  }

  protected class NameColumnInfo extends ElementsColumnInfoBase<EnvironmentVariable> {
    public NameColumnInfo() {
      super(ExecutionBundle.message("env.variable.column.name.title"));
    }
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
    @Override
    public @NotNull TableCellEditor getEditor(EnvironmentVariable variable) {
      return new DefaultCellEditor(new JTextField());
    }
  }

  protected class ValueColumnInfo extends ElementsColumnInfoBase<EnvironmentVariable> {
    public ValueColumnInfo() {
      super(ExecutionBundle.message("env.variable.column.value.title"));
    }
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

    @Override
    protected @Nullable String getDescription(EnvironmentVariable environmentVariable) {
      return environmentVariable.getDescription();
    }

    @Override
    public @NotNull TableCellEditor getEditor(EnvironmentVariable variable) {
      return new StringWithNewLinesCellEditor();
    }
  }

  private final class CopyPasteProviderPanel extends JPanel implements UiDataProvider, CopyProvider, PasteProvider {
    CopyPasteProviderPanel(JComponent component) {
      super(new GridLayout(1, 1));
      add(component);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(PlatformDataKeys.COPY_PROVIDER, this);
      sink.set(PlatformDataKeys.PASTE_PROVIDER, this);
    }

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      TableView<EnvironmentVariable> view = getTableView();
      if (view.isEditing()) {
        int row = view.getEditingRow();
        int column = view.getEditingColumn();
        if (row < 0 || column < 0) {
          row = view.getSelectedRow();
          column = view.getSelectedColumn();
        }
        if (row >= 0 && column >= 0) {
          Component component = ((DefaultCellEditor)view.getCellEditor()).getComponent();
          String text = "";
          if (component instanceof JTextField) {
            text = ((JTextField)component).getSelectedText();
          }
          else if (component instanceof JComboBox<?>) {
            text = ((JTextField)((JComboBox<?>)component).getEditor().getEditorComponent()).getSelectedText();
          }
          else {
            Logger.getInstance(EnvVariablesTable.class).error("Unknown editor type: " + component);
          }
          CopyPasteManager.getInstance().setContents(new StringSelection(text));
        }
        return;
      }
      stopEditing();
      StringBuilder sb = new StringBuilder();
      List<EnvironmentVariable> variables = getSelection();
      for (EnvironmentVariable environmentVariable : variables) {
        if (isEmpty(environmentVariable)) continue;
        if (!sb.isEmpty()) sb.append(';');
        sb.append(StringUtil.escapeChars(environmentVariable.getName(), '=', ';')).append('=')
          .append(StringUtil.escapeChars(environmentVariable.getValue(), '=', ';'));
      }
      CopyPasteManager.getInstance().setContents(new StringSelection(sb.toString()));
    }


    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return !getSelection().isEmpty();
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return isCopyEnabled(dataContext);
    }

    @Override
    public void performPaste(@NotNull DataContext dataContext) {
      String content = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
      if (StringUtil.isEmpty(content)) {
        return;
      }
      Map<String, String> map = parseEnvsFromText(content);
      TableView<EnvironmentVariable> view = getTableView();
      if ((view.isEditing() || map.isEmpty())) {
        int row = view.getEditingRow();
        int column = view.getEditingColumn();
        if (row < 0 || column < 0) {
          row = view.getSelectedRow();
          column = view.getSelectedColumn();
        }
        if (row >= 0 && column >= 0) {
          TableCellEditor editor = view.getCellEditor();
          if (editor != null) {
            Component component = ((DefaultCellEditor)editor).getComponent();
            if (component instanceof JTextField) {
              ((JTextField)component).paste();
            }
          }
        }
        return;
      }
      stopEditing();
      List<EnvironmentVariable> parsed = new ArrayList<>();
      for (Map.Entry<String, String> entry : map.entrySet()) {
        parsed.add(new EnvironmentVariable(entry.getKey(), entry.getValue(), false));
      }
      List<EnvironmentVariable> variables = new ArrayList<>(getEnvironmentVariables());
      variables.addAll(parsed);
      variables = ContainerUtil.filter(variables, variable -> !StringUtil.isEmpty(variable.getName()) ||
                                                              !StringUtil.isEmpty(variable.getValue()));
      setValues(variables);
    }

    @Override
    public boolean isPastePossible(@NotNull DataContext dataContext) {
      return myPasteEnabled;
    }

    @Override
    public boolean isPasteEnabled(@NotNull DataContext dataContext) {
      return myPasteEnabled;
    }
  }

  @Override
  protected AnAction @NotNull [] createExtraToolbarActions() {
    AnAction copyButton = ActionManager.getInstance().getAction(IdeActions.ACTION_COPY);
    AnAction pasteButton = ActionManager.getInstance().getAction(IdeActions.ACTION_PASTE);
    return new AnAction[]{copyButton, pasteButton};
  }

  public static @NotNull Map<String, String> parseEnvsFromText(String content) {
    Map<String, String> result = new LinkedHashMap<>();
    if (content != null && content.contains("=")) {
      boolean legacyFormat = content.contains("\n");
      List<String> pairs;
      if (legacyFormat) {
        pairs = StringUtil.split(content, "\n");
      } else {
        pairs = new ArrayList<>();
        int start = 0;
        int end;
        for (end = content.indexOf(";"); end < content.length(); end = content.indexOf(";", end+1)) {
          if (end == -1) {
            pairs.add(content.substring(start).replace("\\;", ";"));
            break;
          }
          if (end > 0 && (content.charAt(end-1) != '\\')) {
            pairs.add(content.substring(start, end).replace("\\;", ";"));
            start = end + 1;
          }
        }
      }
      for (String pair : pairs) {
        int pos = pair.indexOf('=');
        if (pos <= 0) continue;
        while (pos > 0 && pair.charAt(pos - 1) == '\\') {
          pos = pair.indexOf('=', pos + 1);
        }
        if (pos <= 0) continue;
        pair = pair.replaceAll("[\\\\]","\\\\\\\\");
        result.put(StringUtil.unescapeStringCharacters(pair.substring(0, pos)).trim(),
          StringUtil.unescapeStringCharacters(pair.substring(pos + 1)));
      }
    }
    return result;
  }
}
