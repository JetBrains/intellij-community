// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.editors;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.TableUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBComboBoxLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.event.TableModelEvent;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

/**
 * Solves rendering problems in JTable components when JComboBox objects are used as cell
 * editors components. Known issues of using JComboBox component are the following:
 *   <p>1. Ugly view if row height is small enough
 *   <p>2. Truncated strings in the combobox popup if column width is less than text value width
 *   <p>
 *   <b>How to use:</b>
 *   <p>1. In get {@code getTableCellEditorComponent} method create or use existent
 *   {@code JBComboBoxTableCellEditorComponent} instance<br/>
 *   <p>2. Init component by calling {@code setCell}, {@code setOptions},
 *   {@code setDefaultValue} methods
 *   <p>3. Return the instance
 *
 * @author Konstantin Bulenkov
 * @see JBComboBoxLabel
 */
public class JBComboBoxTableCellEditorComponent extends JBLabel {
  private JTable myTable;
  private int myRow = 0;
  private int myColumn = 0;
  private Object[] myOptions = ArrayUtil.EMPTY_OBJECT_ARRAY;
  private Object myValue;
  public boolean myWide = false;
  private Function<Object, @NlsContexts.ListItem String> myToString = Object::toString;
  private final List<ActionListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private ListCellRenderer myRenderer = BuilderKt.listCellRenderer(row -> {
    row.icon(row.getValue() == myValue ? AllIcons.Actions.Checked : EmptyIcon.ICON_16, null);
    row.text(myToString.fun(row.getValue()), null);
    return Unit.INSTANCE;
  });

  public JBComboBoxTableCellEditorComponent() {
  }

  public JBComboBoxTableCellEditorComponent(JTable table) {
    myTable = table;
  }

  public void setCell(JTable table, int row, int column) {
    setTable(table);
    setRow(row);
    setColumn(column);
  }

  public void setTable(JTable table) {
    myTable = table;
  }

  public void setRow(int row) {
    myRow = row;
  }

  public void setColumn(int column) {
    myColumn = column;
  }

  public Object[] getOptions() {
    return myOptions;
  }

  public void setOptions(Object... options) {
    myOptions = options;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    initAndShowPopup();
  }

  private void initAndShowPopup() {
    final Rectangle rect = myTable.getCellRect(myRow, myColumn, true);
    Point point = new Point(rect.x, rect.y);
    final boolean surrendersFocusOnKeystrokeOldValue = myTable instanceof JBTable ? ((JBTable)myTable).surrendersFocusOnKeyStroke() : myTable.getSurrendersFocusOnKeystroke();
    final JBPopup popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(Arrays.asList(myOptions))
      .setItemChosenCallback(chosen -> {
        myValue = chosen;
        final ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "elementChosen");
        for (ActionListener listener : myListeners) {
          listener.actionPerformed(event);
        }
        TableUtil.stopEditing(myTable);

        myTable.setValueAt(myValue, myRow, myColumn); // on Mac getCellEditorValue() called before myValue is set.
        myTable.tableChanged(new TableModelEvent(myTable.getModel(), myRow));  // force repaint
      })
      .setCancelCallback(() -> {
        TableUtil.stopEditing(myTable);
        return true;
      })
      .addListener(new JBPopupListener() {
        @Override
        public void beforeShown(@NotNull LightweightWindowEvent event) {
          myTable.setSurrendersFocusOnKeystroke(false);
        }

        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          myTable.setSurrendersFocusOnKeystroke(surrendersFocusOnKeystrokeOldValue);
        }
      })
      .setRenderer(myRenderer)
      .setMinSize(myWide ? new Dimension(((int)rect.getSize().getWidth()), -1) : null)
      .createPopup();
    popup.show(new RelativePoint(myTable, point));
  }

  public void setWide(boolean wide) {
    this.myWide = wide;
  }

  public Object getEditorValue() {
    return myValue;
  }

  public void setRenderer(ListCellRenderer renderer) {
    myRenderer = renderer;
  }

  public void setDefaultValue(Object value) {
    myValue = value;
  }

  public void setToString(Function<Object, String> toString) {
    myToString = toString;
  }

  public void addActionListener(ActionListener listener) {
    myListeners.add(listener);
  }

  public Function<Object, String> getToString() {
    return myToString;
  }
}
