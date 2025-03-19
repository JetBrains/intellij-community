package com.intellij.database.csv.ui;

import com.intellij.database.DataGridBundle;
import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvFormatResolverCore;
import com.intellij.database.settings.CsvSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TableUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.table.EditorTextFieldJBTableRowRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

public class CsvFormatsListComponent implements Disposable {

  private final JBTable myTable;

  private List<CsvFormat> myOriginalFormats = ContainerUtil.emptyList();

  private boolean myEditingAllowed;
  private Consumer<CsvFormat> myEditedFormatConsumer;


  private boolean myResetting;
  private final EventDispatcher<ChangeListener> myDispatcher = EventDispatcher.create(ChangeListener.class);

  public CsvFormatsListComponent(@NotNull Disposable parent) {
    Disposer.register(parent, this);

    myTable = new MyTable();
    myTable.setShowGrid(false);
    myTable.setTableHeader(null);
    myTable.setColumnSelectionAllowed(false);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setPreferredScrollableViewportSize(JBUI.size(10, -1));
    myTable.setVisibleRowCount(5);

    installListeners();
  }

  public @NotNull JBTable getComponent() {
    return myTable;
  }

  public void setNameEditingAllowed(boolean allowed) {
    myEditingAllowed = allowed;
  }

  public @NotNull List<CsvFormat> getFormats() {
    return new ArrayList<>(model().getFormats());
  }

  public @Nullable CsvFormat getSelected() {
    FormatsListModel model = model();
    int idx = getSelectedIdx();
    return idx != -1 ? model.getFormat(idx) : null;
  }

  public void reset(@NotNull List<CsvFormat> formats, @Nullable String nameToSelect) {
    int toSelect = CsvFormat.indexOfFormatNamed(formats, nameToSelect);
    if (toSelect == -1) {
      CsvFormat selected = getSelected();
      toSelect = CsvFormat.indexOfFormatNamed(formats, selected != null ? selected.name : null);
    }
    toSelect = toSelect == -1 && !formats.isEmpty() ? 0 : toSelect;

    myResetting = true;
    try {
      FormatsListModel model = model();
      if (myTable.isEditing()) myTable.getCellEditor().cancelCellEditing();
      model.setFormats(formats);
      select(toSelect);
      TableUtil.scrollSelectionToVisible(myTable);
    }
    finally {
      myOriginalFormats = new ArrayList<>(formats);

      myResetting = false;
      fireFormatsChanged();
    }
  }

  public boolean isModified(@NotNull CsvFormat format) {
    List<CsvFormat> formats = CsvSettings.getSettings().getCsvFormats();
    int idx = CsvFormat.indexOfFormatNamed(formats, format.name);
    return idx == -1 || !formats.get(idx).equals(format);
  }

  public void markUnmodified(@NotNull CsvFormat format) {
    int idx = CsvFormat.indexOfFormatNamed(myOriginalFormats, format.name);
    if (idx != -1) {
      myOriginalFormats.set(idx, format);
    }
    myTable.revalidate();
    myTable.repaint(50);
  }

  public void addChangeListener(@NotNull ChangeListener listener) {
    myDispatcher.addListener(listener);
  }

  public void updateSelectedFormat(@NotNull CsvFormat with) {
    int selectedIdx = getSelectedIdx();
    if (selectedIdx != -1) {
      model().setFormat(selectedIdx, with);
    }
  }

  public void resetFormat(@Nullable String formatName) {
    int formatIdx = CsvFormat.indexOfFormatNamed(model().getFormats(), formatName);
    if (formatIdx == -1) return;

    int originalFormatIdx = CsvFormat.indexOfFormatNamed(myOriginalFormats, formatName);
    if (originalFormatIdx == -1) return;

    model().setFormat(formatIdx, myOriginalFormats.get(originalFormatIdx));
  }

  public @NotNull CsvFormat newFormat(@NotNull CsvFormat template) {
    CsvFormat format = new CsvFormat(CsvFormatResolverCore.getNewFormatName(template, model().getFormats()), template.dataRecord, template.headerRecord, template.rowNumbers);
    model().addFormat(format);
    select(model().getRowCount() - 1);
    TableUtil.scrollSelectionToVisible(myTable);
    return format;
  }

  public void editFormatName(@NotNull CsvFormat format, @Nullable Consumer<CsvFormat> doWhenEditingIsComplete) {
    int idx = CsvFormat.indexOfFormatNamed(model().getFormats(), format.name);
    if (idx == -1) {
      throw new AssertionError();
    }

    boolean editingAllowed = myEditingAllowed;
    myEditingAllowed = true;
    try {
      if (myTable.editCellAt(idx, 0)) {
        myEditedFormatConsumer = doWhenEditingIsComplete;
        TableUtil.scrollSelectionToVisible(myTable);
      }
    }
    finally {
      myEditingAllowed = editingAllowed;
    }
  }

  @Override
  public void dispose() {
  }

  private void editingComplete(int rowIdx) {
    if (myEditedFormatConsumer != null && rowIdx != -1) {
      myEditedFormatConsumer.consume(model().getFormat(rowIdx));
      myEditedFormatConsumer = null;
    }
  }

  private int getSelectedIdx() {
    int idx = myTable.getSelectionModel().getMinSelectionIndex();
    return idx >= 0 && idx < myTable.getRowCount() ? idx : -1;
  }

  private void select(int idx) {
    if (idx == -1) {
      myTable.getSelectionModel().clearSelection();
    }
    else {
      myTable.getSelectionModel().setSelectionInterval(idx, idx);
    }
  }

  private @NotNull FormatsListModel model() {
    return (FormatsListModel)myTable.getModel();
  }

  private void fireFormatsChanged() {
    if (!myResetting) {
      myDispatcher.getMulticaster().formatsChanged(this);
    }
  }

  private void installListeners() {
    model().addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        fireFormatsChanged();
      }
    });
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) fireFormatsChanged();
      }
    });
    myTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        Point mouse = new Point(x, y);
        final int row = myTable.rowAtPoint(mouse);
        if (row == -1 || myTable.isEditing() || !myEditingAllowed) return;

        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(null, "Edit Name") {
          @Override
          public PopupStep<?> onChosen(String selectedValue, boolean finalChoice) {
            if (finalChoice) {
              editFormatName(model().getFormat(row), null);
            }
            return super.onChosen(selectedValue, finalChoice);
          }
        }).show(new RelativePoint(comp, mouse));
      }
    });
  }


  public interface ChangeListener extends EventListener {
    void formatsChanged(@NotNull CsvFormatsListComponent formatsListComponent);
  }

  private class MyRenderer extends EditorTextFieldJBTableRowRenderer {
    MyRenderer() {
      super(null, CsvFormatsListComponent.this);
    }

    @Override
    protected String getText(JTable table, int row) {
      CsvFormat format = (CsvFormat)table.getValueAt(row, 0);
      String formatName = StringUtil.notNullize(StringUtil.nullize(format.name), "<unnamed format>");
      boolean modified = isModified(format);
      return formatName + (modified ? "*" : "");
    }
  }

  private final class MyEditor extends AbstractTableCellEditor implements Disposable {
    private final EditorTextField myTextField;
    private CsvFormat myFormat;

    private MyEditor() {
      myTextField = new EditorTextField("");
      myTextField.setOneLineMode(true);
      myTextField.setFontInheritedFromLAF(true);
      myTextField.setSupplementary(true);
      myTextField.putClientProperty("JBListTable.isTableCellEditor", Boolean.TRUE);
      new DumbAwareAction(DataGridBundle.messagePointer("action.CsvFormatsListComponent.stop.editing.text")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          stopCellEditing();
        }
      }.registerCustomShortcutSet(CommonShortcuts.ENTER, myTextField, this);
      Disposer.register(CsvFormatsListComponent.this, this);
    }

    @Override
    public boolean isCellEditable(EventObject e) {
      if (e instanceof MouseEvent) {
        return ((MouseEvent)e).getClickCount() >= 2;
      }
      return super.isCellEditable(e);
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      myFormat = (CsvFormat)value;
      myTextField.setText(myFormat.name);
      myTextField.selectAll();
      return myTextField;
    }

    @Override
    public Object getCellEditorValue() {
      return new CsvFormat(myTextField.getText(), myFormat.dataRecord, myFormat.headerRecord, myFormat.id, myFormat.rowNumbers);
    }

    @Override
    public boolean stopCellEditing() {
      String formatName = myTextField.getText();
      if (StringUtil.isEmpty(formatName)) {
        //TODO report empty names are not allowed
        return false;
      }

      int idx = CsvFormat.indexOfFormatNamed(model().getFormats(), formatName);
      CsvFormat format = idx != -1 ? model().getFormat(idx) : null;
      if (format != null && format != myFormat) {
        //TODO report this name is not unique
        return false;
      }

      return super.stopCellEditing();
    }

    @Override
    public void dispose() {
      myTextField.removeNotify();
    }
  }

  private class MyTable extends JBTable {
    TableCellRenderer renderer = new MyRenderer();
    TableCellEditor editor = new MyEditor();

    MyTable() {
      super(new FormatsListModel());
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
      return renderer;
    }

    @Override
    public TableCellEditor getCellEditor(int row, int column) {
      return editor;
    }

    @Override
    public void editingStopped(ChangeEvent e) {
      int editedRowIdx = getEditingRow();
      super.editingStopped(e);
      editingComplete(editedRowIdx);
    }

    @Override
    public void editingCanceled(ChangeEvent e) {
      int editedRowIdx = getEditingRow();
      super.editingCanceled(e);
      editingComplete(editedRowIdx);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return myEditingAllowed;
    }
  }

  private static class FormatsListModel extends AbstractTableModel implements EditableModel {
    private final List<CsvFormat> myFormats = new ArrayList<>();

    @Override
    public int getRowCount() {
      return myFormats.size();
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return myFormats.get(rowIndex);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      myFormats.set(rowIndex, (CsvFormat)aValue);
      fireTableCellUpdated(rowIndex, 0);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return true;
    }

    @Override
    public void addRow() {
      throw new AssertionError("Should not be called");
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
      CsvFormat atOld = myFormats.get(oldIndex);
      myFormats.set(oldIndex, myFormats.get(newIndex));
      fireTableCellUpdated(oldIndex, 0);
      myFormats.set(newIndex, atOld);
      fireTableCellUpdated(newIndex, 0);
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return true;
    }

    @Override
    public void removeRow(int idx) {
      myFormats.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }

    public @NotNull List<CsvFormat> getFormats() {
      return Collections.unmodifiableList(myFormats);
    }

    public @NotNull CsvFormat getFormat(int idx) {
      return myFormats.get(idx);
    }

    public void setFormats(@NotNull List<CsvFormat> formats) {
      myFormats.clear();
      myFormats.addAll(formats);
      fireTableDataChanged();
    }

    public void setFormat(int idx, @NotNull CsvFormat format) {
      setValueAt(format, idx, 0);
    }

    public void addFormat(@NotNull CsvFormat format) {
      myFormats.add(format);
      fireTableRowsInserted(myFormats.size() - 1, myFormats.size() - 1);
    }
  }
}
