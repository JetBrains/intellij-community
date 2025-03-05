// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor;

import com.intellij.execution.util.ListTableWithButtons;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.cellvalidators.CellComponentProvider;
import com.intellij.openapi.ui.cellvalidators.CellTooltipManager;
import com.intellij.openapi.ui.cellvalidators.ValidatingTableCellRendererWrapper;
import com.intellij.openapi.ui.cellvalidators.ValidationUtils;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

abstract class ImportTable extends ListTableWithButtons<ImportTable.Item> implements Disposable.Default {
  private static final Pattern ourPackagePattern = Pattern.compile("([\\w*]+\\.)*[\\w*]+");

  private static final BiFunction<Object, JComponent, ValidationInfo> validationInfoProducer = (value, component) ->
    (value == null || StringUtil.isEmpty(value.toString()) || ourPackagePattern.matcher(value.toString()).matches()) ?
    null : new ValidationInfo(JavaBundle.message("illegal.name.validation.info", value.toString()), component);

  private static final Disposable validatorsDisposable = Disposer.newDisposable();

  private final @NotNull @Nls String myNameColumn;
  private final @NotNull @Nls String myScopeColumn;


  ImportTable(@NotNull Disposable parentDisposable,
              @NlsSafe @NotNull String messageLine,
              @NotNull @Nls String nameColumn,
              @NotNull @Nls String scopeColumn) {
    myNameColumn = nameColumn;
    myScopeColumn = scopeColumn;
    JBTable table = getTableView();
    table.getEmptyText().clear();
    table.getEmptyText().appendLine(messageLine);
    table.setStriped(false);
    new CellTooltipManager(parentDisposable).withCellComponentProvider(CellComponentProvider.forTable(table)).installOn(table);
    Disposer.register(parentDisposable, validatorsDisposable);
  }

  @Override
  protected ListTableModel<Item> createListModel() {
    return new ListTableModel<>(createNameColumn(), createScopeColumn());
  }

  private ColumnInfo<Item, Scope> createScopeColumn() {
    return new ColumnInfo<>(myScopeColumn) {
      @Override
      public @Nullable ImportTable.Scope valueOf(Item pair) {
        return pair.scope;
      }

      @Override
      public TableCellRenderer getRenderer(Item pair) {
        return new ComboBoxTableRenderer<>(Scope.values())
          .withClickCount(1);
      }

      @Override
      public TableCellEditor getEditor(Item pair) {
        return new ComboBoxTableRenderer<>(Scope.values())
          .withClickCount(1);
      }

      @Override
      public boolean isCellEditable(Item pair) {
        return true;
      }

      @Override
      public void setValue(Item pair, Scope value) {
        pair.scope = value;
      }

      @Override
      public String getMaxStringValue() {
        return "Project";
      }

      @Override
      public int getAdditionalWidth() {
        return JBUIScale.scale(12) + AllIcons.General.ArrowDown.getIconWidth();
      }
    };
  }

  private ColumnInfo<Item, String> createNameColumn() {
    return new ColumnInfo<>(myNameColumn) {

      @Override
      public @Nullable String valueOf(Item pair) {
        return pair.row;
      }

      @Override
      public TableCellEditor getEditor(Item pair) {
        ExtendableTextField cellEditor = new ExtendableTextField();
        cellEditor.putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, Boolean.TRUE);

        new ComponentValidator(validatorsDisposable).withValidator(() -> {
          String text = cellEditor.getText();
          boolean hasError = !StringUtil.isEmpty(text) && !ourPackagePattern.matcher(text).matches();
          ValidationUtils.setExtension(cellEditor, ValidationUtils.ERROR_EXTENSION, hasError);

          return validationInfoProducer.apply(text, cellEditor);
        }).andRegisterOnDocumentListener(cellEditor).installOn(cellEditor);

        return new DefaultCellEditor(cellEditor);
      }

      @Override
      public TableCellRenderer getRenderer(Item pair) {
        JTextField cellEditor = new JTextField();
        cellEditor.putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, Boolean.TRUE);

        return new ValidatingTableCellRendererWrapper(new DefaultTableCellRenderer()).
          withCellValidator((value, row, column) -> validationInfoProducer.apply(value, null)).
          bindToEditorSize(cellEditor::getPreferredSize);
      }

      @Override
      public boolean isCellEditable(Item pair) {
        return true;
      }

      @Override
      public void setValue(Item item, String value) {
        item.row = value;
      }
    };
  }

  @Override
  protected Item createElement() {
    return new Item("", Scope.IDE);
  }

  @Override
  protected boolean isEmpty(Item element) {
    return element.row.isEmpty();
  }

  @Override
  protected Item cloneElement(Item variable) {
    return new Item(variable.row, variable.scope);
  }

  @Override
  protected boolean canDeleteElement(Item selection) {
    return true;
  }

  void addRow(@NotNull String row) {
    int index = 0;
    while (index < getTableView().getListTableModel().getRowCount()) {
      if (getTableView().getListTableModel().getItem(index).row.compareTo(row) > 0) {
        break;
      }
      index++;
    }

    getTableView().getListTableModel().insertRow(index, new Item(row, Scope.IDE));
    getTableView().clearSelection();
    getTableView().addRowSelectionInterval(index, index);
    ScrollingUtil.ensureIndexIsVisible(getTableView(), index, 0);
    IdeFocusManager.getGlobalInstance().requestFocus(getTableView(), false);
  }

  void reset() {
    List<Item> rows = new ArrayList<>();
    for (String s : getIdeRows()) {
      rows.add(new Item(s, Scope.IDE));
    }
    for (String s : getProjectRows()) {
      rows.add(new Item(s, Scope.Project));
    }
    rows.sort(Comparator.comparing(o -> o.row));

    setValues(rows);
  }

  void apply() {
    setIdeRows(ArrayUtilRt.toStringArray(getRows(Scope.IDE)));
    setProjectRows(getRows(Scope.Project).toArray(ArrayUtilRt.EMPTY_STRING_ARRAY));
  }

  protected abstract @NotNull String @NotNull [] getIdeRows();

  protected abstract @NotNull String @NotNull [] getProjectRows();

  protected abstract void setIdeRows(@NotNull String @NotNull [] rows);

  protected abstract void setProjectRows(@NotNull String @NotNull [] rows);

  private List<String> getRows(Scope scope) {
    List<String> result = new ArrayList<>();
    for (Item pair : getTableView().getListTableModel().getItems()) {
      if (scope == pair.scope) {
        result.add(pair.row);
      }
    }
    Collections.sort(result);
    return result;
  }

  boolean isModified() {
    return !Arrays.equals(ArrayUtilRt.toStringArray(getRows(Scope.IDE)), getIdeRows())
           ||
           !Arrays.equals(ArrayUtilRt.toStringArray(getRows(Scope.Project)), getProjectRows());
  }

  private enum Scope {Project, IDE}

  static class Item {
    String row;
    Scope scope;

    Item(@NotNull String row, Scope scope) {
      this.row = row;
      this.scope = scope;
    }
  }
}