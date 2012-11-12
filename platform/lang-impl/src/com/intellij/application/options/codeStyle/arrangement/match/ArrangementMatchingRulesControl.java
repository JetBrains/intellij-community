/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.component.ArrangementEditorAware;
import com.intellij.application.options.codeStyle.arrangement.component.ArrangementEditorComponent;
import com.intellij.application.options.codeStyle.arrangement.component.ArrangementMatchNodeComponentFactory;
import com.intellij.application.options.codeStyle.arrangement.component.ArrangementRepresentationAware;
import com.intellij.application.options.codeStyle.arrangement.util.ArrangementListRowDecorator;
import com.intellij.application.options.codeStyle.arrangement.util.IntObjectMap;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 10/31/12 1:23 PM
 */
public class ArrangementMatchingRulesControl extends JBTable {

  @NotNull private static final Logger LOG            = Logger.getInstance("#" + ArrangementMatchingRulesControl.class.getName());
  @NotNull private static final JLabel EMPTY_RENDERER = new JLabel(ApplicationBundle.message("arrangement.text.empty.rule"));

  @NotNull private final IntObjectMap<ArrangementListRowDecorator> myComponents = new IntObjectMap<ArrangementListRowDecorator>();

  @NotNull private final ArrangementMatchNodeComponentFactory myFactory;
  @NotNull private final ArrangementMatchingRuleEditor        myEditor;
  @NotNull private final RepresentationCallback               myRepresentationCallback;
  @NotNull private final MyRenderer                           myRenderer;

  private int myRowUnderMouse = -1;
  private int myEditorRow     = -1;
  private boolean mySkipSelectionChange;

  public ArrangementMatchingRulesControl(@NotNull ArrangementNodeDisplayManager displayManager,
                                         @NotNull ArrangementColorsProvider colorsProvider,
                                         @NotNull ArrangementStandardSettingsAware settingsFilter,
                                         @NotNull RepresentationCallback callback)
  {
    super(new ArrangementMatchingRulesModel());
    myRepresentationCallback = callback;
    myFactory = new ArrangementMatchNodeComponentFactory(displayManager, colorsProvider, this);
    myRenderer = new MyRenderer();
    setDefaultRenderer(Object.class, myRenderer);
    getColumnModel().getColumn(0).setCellEditor(new MyEditor());
    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    setShowColumns(false);
    setShowGrid(false);
    myEditor = new ArrangementMatchingRuleEditor(settingsFilter, colorsProvider, displayManager, this);
    addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        onMouseMoved(e);
      }
    });
    getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        onSelectionChange(e);
      }
    });
  }

  @NotNull
  @Override
  public ArrangementMatchingRulesModel getModel() {
    return (ArrangementMatchingRulesModel)super.getModel();
  }

  public void setRules(@Nullable List<StdArrangementMatchRule> rules) {
    myComponents.clear();
    getModel().clear();

    if (rules == null) {
      return;
    }

    for (StdArrangementMatchRule rule : rules) {
      getModel().add(rule);
    }

    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info("Arrangement matching rules list is refreshed. Given rules:");
      for (StdArrangementMatchRule rule : rules) {
        LOG.info("  " + rule.toString());
      }
    }
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    int id = e.getID();
    switch (id) {
      case MouseEvent.MOUSE_ENTERED: onMouseEntered(e); break;
      case MouseEvent.MOUSE_EXITED: onMouseExited(); break;
      case MouseEvent.MOUSE_RELEASED: onMouseReleased(e); break;
    }
    if (!e.isConsumed()) {
      super.processMouseEvent(e);
    }
  }

  private void onMouseMoved(@NotNull MouseEvent e) {
    int i = rowAtPoint(e.getPoint());
    if (i != myRowUnderMouse) {
      onMouseExited();
    }
    
    if (i < 0) {
      return;
    }

    if (i != myRowUnderMouse) {
      onMouseEntered(e);
    }

    ArrangementListRowDecorator decorator = myComponents.get(i);
    if (decorator == null) {
      return;
    }

    Rectangle rectangle = decorator.onMouseMove(e);
    if (rectangle != null) {
      repaintScreenBounds(rectangle);
    }
  }

  private void repaintScreenBounds(@NotNull Rectangle bounds) {
    Point location = bounds.getLocation();
    SwingUtilities.convertPointFromScreen(location, this);
    int x = location.x;
    int width = bounds.width;
    repaint(x, location.y, width, bounds.height);
  }
  
  
  private void onMouseReleased(@NotNull MouseEvent e) {
    int i = rowAtPoint(e.getPoint());
    if (i < 0) {
      return;
    }

    ArrangementListRowDecorator decorator = myComponents.get(i);
    if (decorator != null) {
      decorator.onMouseRelease(e);
    }
    
    if (!e.isConsumed() && myEditorRow > 0 && myEditorRow == i + 1) {
      hideEditor();
    }
    //else {
    //  ListSelectionModel selectionModel = getSelectionModel();
    //  if (myEditorRow < 0
    //      && selectionModel.isSelectedIndex(i)
    //      && selectionModel.getMinSelectionIndex() == selectionModel.getMaxSelectionIndex())
    //  {
    //    
    //    showEditor(i);
    //  }
    //} 
    
  }
  
  private void onMouseExited() {
    if (myRowUnderMouse < 0) {
      return;
    }

    ArrangementListRowDecorator decorator = myComponents.get(myRowUnderMouse);
    if (decorator != null) {
      decorator.onMouseExited();
      repaintRows(myRowUnderMouse, myRowUnderMouse, false);
    }
    myRowUnderMouse = -1;
  }

  private void onMouseEntered(@NotNull MouseEvent e) {
    myRowUnderMouse = rowAtPoint(e.getPoint());
    ArrangementListRowDecorator decorator = myComponents.get(myRowUnderMouse);
    if (decorator != null) {
      decorator.onMouseEntered(e);
      repaintRows(myRowUnderMouse, myRowUnderMouse, false);
    }
  }

  private void onSelectionChange(@NotNull ListSelectionEvent e) {
    if (mySkipSelectionChange || e.getValueIsAdjusting()) {
      return;
    }
    ListSelectionModel selectionModel = getSelectionModel();
    if (selectionModel.isSelectionEmpty()) {
      hideEditor();
      return;
    }

    int selectedRow = selectionModel.getMinSelectionIndex();
    if (selectedRow != selectionModel.getMaxSelectionIndex()) {
      // More than one row is selected.
      hideEditor();
      return;
    }

    if (myEditorRow >= 0) {
      if (myEditorRow == selectedRow) {
        mySkipSelectionChange = true;
        try {
          getSelectionModel().setSelectionInterval(myEditorRow - 1, myEditorRow - 1);
        }
        finally {
          mySkipSelectionChange = false;
        }
        return;
      }
      else {
        hideEditor();
      }
    }
    
    // There is a possible case that there was an active editor in a row before the selected.
    selectedRow = selectionModel.getMinSelectionIndex();
    ArrangementListRowDecorator toEdit = myComponents.get(selectedRow);
    if (toEdit != null) {
      showEditor(selectedRow);
    }
  }

  private void hideEditor() {
    if (myEditorRow < 0) {
      return;
    }
    repaintRows(0, getModel().getSize() - 1, false); // Update 'selected' status
    myComponents.shiftKeys(myEditorRow, - 1);
    mySkipSelectionChange = true;
    try {
      getModel().removeRow(myEditorRow);
      getSelectionModel().clearSelection();
    }
    finally {
      mySkipSelectionChange = false;
    }
    myEditorRow = -1;
  }

  private void showEditor(int selectedRow) {
    myEditorRow = selectedRow + 1;
    ArrangementEditorComponent editor = new ArrangementEditorComponent(this, myEditorRow, myEditor);
    Container parent = getParent();
    int width = getBounds().width;
    if (parent instanceof JViewport) {
      width -=((JScrollPane)parent.getParent()).getVerticalScrollBar().getWidth();
    }
    editor.applyAvailableWidth(width);
    myEditor.updateState(selectedRow);
    myComponents.shiftKeys(myEditorRow, 1);
    mySkipSelectionChange = true;
    try {
      getModel().insertRow(myEditorRow, new Object[]{editor});
    }
    finally {
      mySkipSelectionChange = false;
    }
    
    Rectangle bounds = getRowsBounds(selectedRow, myEditorRow);
    if (bounds != null) {
      myRepresentationCallback.ensureVisible(bounds);
    }

    // We can't just subscribe to the model modification events and update cached renderers automatically because we need to use
    // the cached renderer on atom condition removal (via click on 'close' button). The model is modified immediately then but
    // corresponding cached renderer is used for animation.
    editor.expand();
    repaintRows(selectedRow, getModel().getRowCount() - 1, false);
    editCellAt(myEditorRow, 0);
  }

  @NotNull
  public List<StdArrangementMatchRule> getRules() {
    if (getModel().getSize() <= 0) {
      return Collections.emptyList();
    }
    List<StdArrangementMatchRule> result = new ArrayList<StdArrangementMatchRule>();
    for (int i = 0; i < getModel().getSize(); i++) {
      Object element = getModel().getElementAt(i);
      if (element instanceof StdArrangementMatchRule) {
        result.add((StdArrangementMatchRule)element);
      }
    }
    return result;
  }
  
  public void repaintRows(int first, int last, boolean rowStructureChanged) {
    for (int i = first; i <= last; i++) {
      if (rowStructureChanged) {
        myComponents.remove(i);
      }
      else {
        setRowHeight(i, myRenderer.getRendererComponent(i).getPreferredSize().height);
      }
    }
    getModel().fireTableRowsUpdated(first, last);
  }

  private Rectangle getRowsBounds(int first, int last) {
    Rectangle firstRect = getCellRect(first, 0, true);
    Rectangle lastRect = getCellRect(last, 0, true);
    return new Rectangle(firstRect.x, firstRect.y, lastRect.width, lastRect.y + lastRect.height - firstRect.y);
  }

  private class MyRenderer implements TableCellRenderer {

    public Component getRendererComponent(int row) {
      return getTableCellRendererComponent(ArrangementMatchingRulesControl.this, getModel().getElementAt(row), false, false, row, 0);
    }
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (value instanceof ArrangementRepresentationAware) {
        return ((ArrangementRepresentationAware)value).getComponent();
      }
      ArrangementListRowDecorator component = myComponents.get(row);
      if (component == null) {
        if (!(value instanceof StdArrangementMatchRule)) {
          return EMPTY_RENDERER;
        }
        StdArrangementMatchRule rule = (StdArrangementMatchRule)value;
        component = new ArrangementListRowDecorator(myFactory.getComponent(rule.getMatcher().getCondition(), rule, true));
        myComponents.set(row, component);
        if (myRowUnderMouse == row) {
          component.setBackground(UIUtil.getDecoratedRowColor());
        }
        else {
          component.setBackground(UIUtil.getListBackground());
        }
        setRowHeight(row, component.getPreferredSize().height);
      }
      component.setRowIndex((myEditorRow >= 0 && row > myEditorRow) ? row : row + 1);
      component.setSelected(getSelectionModel().isSelectedIndex(row) || (myEditorRow >= 0 && row == myEditorRow - 1));
      return component.getUiComponent();
    }
  }
  
  private static class MyEditor extends AbstractTableCellEditor {
    
    @Nullable private Object myValue;
    
    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      if (value instanceof ArrangementEditorAware) {
        myValue = value;
        return ((ArrangementEditorAware)value).getComponent();
      }
      return null;
    }

    @Override
    public Object getCellEditorValue() {
      return myValue;
    }

    @Override
    public boolean stopCellEditing() {
      myValue = null;
      return true;
    }
  }
  
  public interface RepresentationCallback {
    void ensureVisible(@NotNull Rectangle r);
  }
}
