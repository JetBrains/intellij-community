/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.util.ui.ItemRemovable;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
* @author nik
*/
class ClasspathTableModel extends AbstractTableModel implements ItemRemovable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.classpath.ClasspathTableModel");
  public static final String EXPORT_COLUMN_NAME = ProjectBundle.message("modules.order.export.export.column");
  private static final String SCOPE_COLUMN_NAME = ProjectBundle.message("modules.order.export.scope.column");
  public static final int EXPORT_COLUMN = 0;
  public static final int ITEM_COLUMN = 1;
  public static final int SCOPE_COLUMN = 2;
  private final List<ClasspathTableItem<?>> myItems = new ArrayList<ClasspathTableItem<?>>();
  private final ModuleConfigurationState myState;
  private StructureConfigurableContext myContext;

  public ClasspathTableModel(final ModuleConfigurationState state, StructureConfigurableContext context) {
    myState = state;
    myContext = context;
    init();
  }

  private ModifiableRootModel getModel() {
    return myState.getRootModel();
  }

  public void init() {
    final OrderEntry[] orderEntries = getModel().getOrderEntries();
    boolean hasJdkOrderEntry = false;
    for (final OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof JdkOrderEntry) {
        hasJdkOrderEntry = true;
      }
      addItem(ClasspathTableItem.createItem(orderEntry, myContext));
    }
    if (!hasJdkOrderEntry) {
      addItemAt(new InvalidJdkItem(), 0);
    }
  }

  public ClasspathTableItem<?> getItemAt(int row) {
    return myItems.get(row);
  }

  public void addItem(ClasspathTableItem<?> item) {
    myItems.add(item);
  }

  public void addItemAt(ClasspathTableItem<?> item, int row) {
    myItems.add(row, item);
  }

  public ClasspathTableItem<?> removeDataRow(int row) {
    return myItems.remove(row);
  }


  @Override
  public void removeRow(int row) {
    removeDataRow(row);
  }

  public void clear() {
    myItems.clear();
  }

  @Override
  public int getRowCount() {
    return myItems.size();
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    final ClasspathTableItem<?> item = myItems.get(rowIndex);
    if (columnIndex == EXPORT_COLUMN) {
      return item.isExported();
    }
    if (columnIndex == SCOPE_COLUMN) {
      return item.getScope();
    }
    if (columnIndex == ITEM_COLUMN) {
      return item;
    }
    LOG.error("Incorrect column index: " + columnIndex);
    return null;
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    final ClasspathTableItem<?> item = myItems.get(rowIndex);
    if (columnIndex == EXPORT_COLUMN) {
      item.setExported(((Boolean)aValue).booleanValue());
    }
    else if (columnIndex == SCOPE_COLUMN && aValue instanceof DependencyScope) {
      item.setScope((DependencyScope) aValue);
    }
  }

  @Override
  public String getColumnName(int column) {
    if (column == EXPORT_COLUMN) {
      return EXPORT_COLUMN_NAME;
    }
    if (column == SCOPE_COLUMN) {
      return SCOPE_COLUMN_NAME;
    }
    return "";
  }

  @Override
  public Class getColumnClass(int column) {
    if (column == EXPORT_COLUMN) {
      return Boolean.class;
    }
    if (column == SCOPE_COLUMN) {
      return DependencyScope.class;
    }
    if (column == ITEM_COLUMN) {
      return ClasspathTableItem.class;
    }
    return super.getColumnClass(column);
  }

  @Override
  public int getColumnCount() {
    return 3;
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    if (column == EXPORT_COLUMN || column == SCOPE_COLUMN) {
      final ClasspathTableItem<?> item = myItems.get(row);
      return item != null && item.isExportable();
    }
    return false;
  }
}
