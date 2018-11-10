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
package com.intellij.ui.treeStructure.treetable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.EventObject;

/**
 * TreeTableCellEditor implementation. Component returned is the
 * JTree.
 */
public class TreeTableCellEditor extends AbstractCellEditor implements TableCellEditor {
  private final TableCellRenderer myTableCellRenderer;

  public TreeTableCellEditor(TableCellRenderer tableCellRenderer) {
    myTableCellRenderer = tableCellRenderer;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column){
    return myTableCellRenderer.getTableCellRendererComponent(table, value, isSelected, false, 0, column);
  }

  /**
   * Overridden to return false, and if the event is a mouse event
   * it is forwarded to the tree.<p>
   * The behavior for this is debatable, and should really be offered
   * as a property. By returning false, all keyboard actions are
   * implemented in terms of the table. By returning true, the
   * tree would get a chance to do something with the keyboard
   * events. For the most part this is ok. But for certain keys,
   * such as left/right, the tree will expand/collapse where as
   * the table focus should really move to a different column. Page
   * up/down should also be implemented in terms of the table.
   * By returning false this also has the added benefit that clicking
   * outside of the bounds of the tree node, but still in the tree
   * column will select the row, whereas if this returned true
   * that wouldn't be the case.
   * <p>By returning false we are also enforcing the policy that
   * the tree will never be editable (at least by a key sequence).
   */
  @Override
  public boolean isCellEditable(EventObject e){
    return false;
  }


  @Override
  public Object getCellEditorValue() {
    return "";
  }
}
