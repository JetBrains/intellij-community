// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.execution.util.ListTableWithButtons;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollingUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

class ExcludeTable extends ListTableWithButtons<ExcludeTable.Item> {
  private static final Pattern ourPackagePattern = Pattern.compile("([\\w*]+\\.)*[\\w*]+");
  private static final ColumnInfo<Item, String> NAME_COLUMN = new ColumnInfo<Item, String>("Class/package/member qualified name mask") {
    @Nullable
    @Override
    public String valueOf(Item pair) {
      return pair.exclude;
    }

    @Override
    public TableCellEditor getEditor(Item pair) {
      final JTextField field = GuiUtils.createUndoableTextField();
      field.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          field.putClientProperty("JComponent.outline",
                                  ourPackagePattern.matcher(field.getText()).matches() ? null : "error");
        }
      });
      return new DefaultCellEditor(field);
    }

    @Override
    public TableCellRenderer getRenderer(Item pair) {
      return new DefaultTableCellRenderer() {
        @NotNull
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (!ourPackagePattern.matcher((String)value).matches()) {
            component.setForeground(JBColor.RED);
          }
          return component;
        }
      };
    }

    @Override
    public boolean isCellEditable(Item pair) {
      return true;
    }

    @Override
    public void setValue(Item item, String value) {
      item.exclude = value;
    }
  };
  private static final ColumnInfo<Item, ExclusionScope> SCOPE_COLUMN = new ColumnInfo<Item, ExclusionScope>("Scope") {
    @Nullable
    @Override
    public ExclusionScope valueOf(Item pair) {
      return pair.scope;
    }

    @Override
    public TableCellRenderer getRenderer(Item pair) {
      return new ComboBoxTableRenderer<>(ExclusionScope.values());
    }

    @Override
    public TableCellEditor getEditor(Item pair) {
      return new ComboBoxTableRenderer<>(ExclusionScope.values());
    }

    @Override
    public boolean isCellEditable(Item pair) {
      return true;
    }

    @Override
    public void setValue(Item pair, ExclusionScope value) {
      pair.scope = value;
    }

    @Override
    public String getMaxStringValue() {
      return "Project";
    }

    @Override
    public int getAdditionalWidth() {
      return JBUI.scale(12) + AllIcons.General.ArrowDown.getIconWidth();
    }
  };
  private final Project myProject;

  ExcludeTable(@NotNull Project project) {
    myProject = project;
    getTableView().getEmptyText().setText(ApplicationBundle.message("exclude.from.imports.no.exclusions"));
  }

  @Override
  protected ListTableModel createListModel() {
    return new ListTableModel<Item>(NAME_COLUMN, SCOPE_COLUMN);
  }

  @Override
  protected Item createElement() {
    return new Item("", ExclusionScope.IDE);
  }

  @Override
  protected boolean isEmpty(Item element) {
    return element.exclude.isEmpty();
  }

  @Override
  protected Item cloneElement(Item variable) {
    return new Item(variable.exclude, variable.scope);
  }

  @Override
  protected boolean canDeleteElement(Item selection) {
    return true;
  }

  void addExcludePackage(String packageName) {
    if (packageName == null) {
      return;
    }

    int index = 0;
    while (index < getTableView().getListTableModel().getRowCount()) {
      if (getTableView().getListTableModel().getItem(index).exclude.compareTo(packageName) > 0) {
        break;
      }
      index++;
    }

    getTableView().getListTableModel().insertRow(index, new Item(packageName, ExclusionScope.IDE));
    getTableView().clearSelection();
    getTableView().addRowSelectionInterval(index, index);
    ScrollingUtil.ensureIndexIsVisible(getTableView(), index, 0);
    IdeFocusManager.getGlobalInstance().requestFocus(getTableView(), false);
  }

  void reset() {
    java.util.List<Item> rows = ContainerUtil.newArrayList();
    for (String s : CodeInsightSettings.getInstance().EXCLUDED_PACKAGES) {
      rows.add(new Item(s, ExclusionScope.IDE));
    }
    for (String s : JavaProjectCodeInsightSettings.getSettings(myProject).excludedNames) {
      rows.add(new Item(s, ExclusionScope.Project));
    }
    Collections.sort(rows, Comparator.comparing(o -> o.exclude));

    setValues(rows);
  }

  void apply() {
    CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = ArrayUtil.toStringArray(getExcludedPackages(ExclusionScope.IDE));
    JavaProjectCodeInsightSettings.getSettings(myProject).excludedNames = getExcludedPackages(ExclusionScope.Project);
  }

  private List<String> getExcludedPackages(ExclusionScope scope) {
    List<String> result = ContainerUtil.newArrayList();
    for (Item pair : getTableView().getListTableModel().getItems()) {
      if (scope == pair.scope) {
        result.add(pair.exclude);
      }
    }
    Collections.sort(result);
    return result;
  }

  boolean isModified() {
    return !getExcludedPackages(ExclusionScope.IDE).equals(Arrays.asList(CodeInsightSettings.getInstance().EXCLUDED_PACKAGES))
           ||
           !getExcludedPackages(ExclusionScope.Project).equals(JavaProjectCodeInsightSettings.getSettings(myProject).excludedNames);

  }

  private enum ExclusionScope {Project, IDE}

  static class Item {
    String exclude;
    ExclusionScope scope;

    Item(@NotNull String exclude, ExclusionScope scope) {
      this.exclude = exclude;
      this.scope = scope;
    }
  }
}