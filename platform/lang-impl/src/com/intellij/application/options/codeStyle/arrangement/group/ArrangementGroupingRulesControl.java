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
package com.intellij.application.options.codeStyle.arrangement.group;

import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.application.options.codeStyle.arrangement.component.ArrangementEditorAware;
import com.intellij.application.options.codeStyle.arrangement.component.ArrangementRepresentationAware;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType;
import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 11/13/12 7:27 PM
 */
public class ArrangementGroupingRulesControl extends JBTable {

  @NotNull private final Map<ArrangementGroupingType, ArrangementGroupingComponent> myComponents
    = new EnumMap<ArrangementGroupingType, ArrangementGroupingComponent>(ArrangementGroupingType.class);

  public ArrangementGroupingRulesControl(@NotNull ArrangementNodeDisplayManager displayManager,
                                         @NotNull ArrangementStandardSettingsAware settingsFilter)
  {
    super(new DefaultTableModel(0, 1));
    setDefaultRenderer(Object.class, new MyRenderer());
    getColumnModel().getColumn(0).setCellEditor(new MyEditor());
    setShowColumns(false);
    setShowGrid(false);
    setBorder(IdeBorderFactory.createBorder());
    putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

    for (ArrangementGroupingType groupingType : ArrangementGroupingType.values()) {
      if (!settingsFilter.isEnabled(groupingType, null)) {
        continue;
      }
      List<ArrangementEntryOrderType> orderTypes = new ArrayList<ArrangementEntryOrderType>();
      for (ArrangementEntryOrderType orderType : ArrangementEntryOrderType.values()) {
        if (settingsFilter.isEnabled(groupingType, orderType)) {
          orderTypes.add(orderType);
        }
      }
      ArrangementGroupingComponent component = new ArrangementGroupingComponent(displayManager, groupingType, orderTypes);
      myComponents.put(groupingType, component);
      getModel().addRow(new Object[]{component});
    }
  }

  @Override
  public DefaultTableModel getModel() {
    return (DefaultTableModel)super.getModel();
  }

  public void setRules(@Nullable List<ArrangementGroupingRule> rules) {
    for (ArrangementGroupingComponent component : myComponents.values()) {
      component.setChecked(false);
    }
    
    if (rules == null) {
      return;
    }

    DefaultTableModel model = getModel();
    while (model.getRowCount() > 0) {
      model.removeRow(model.getRowCount() - 1);
    }

    List<ArrangementGroupingType> types = new ArrayList<ArrangementGroupingType>(myComponents.keySet());
    ContainerUtil.sort(types, new MyComparator(rules));
    for (ArrangementGroupingType type : types) {
      model.addRow(new Object[] { myComponents.get(type) });
    }
    for (ArrangementGroupingRule rule : rules) {
      ArrangementGroupingComponent component = myComponents.get(rule.getGroupingType());
      component.setChecked(true);
      ArrangementEntryOrderType orderType = rule.getOrderType();
      component.setOrderType(orderType);
    }
  }

  @NotNull
  public List<ArrangementGroupingRule> getRules() {
    List<ArrangementGroupingRule> result = new ArrayList<ArrangementGroupingRule>();
    DefaultTableModel model = getModel();
    for (int i = 0, max = model.getRowCount(); i < max; i++) {
      ArrangementGroupingComponent component = (ArrangementGroupingComponent)model.getValueAt(i, 0);
      if (!component.isChecked()) {
        continue;
      }
      ArrangementEntryOrderType orderType = component.getOrderType();
      if (orderType == null) {
        result.add(new ArrangementGroupingRule(component.getGroupingType()));
      }
      else {
        result.add(new ArrangementGroupingRule(component.getGroupingType(), orderType));
      }
    }
    return result;
  }

  private static class MyRenderer implements TableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (value instanceof ArrangementGroupingComponent) {
        ArrangementGroupingComponent component = (ArrangementGroupingComponent)value;
        component.setRowIndex(row + 1);
        return component;
      }
      else if (value instanceof ArrangementRepresentationAware) {
        return ((ArrangementRepresentationAware)value).getComponent();
      }
      return null;
    }
  }
  
  private static class MyEditor extends AbstractTableCellEditor {
    
    @Nullable Object myValue;
    
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
      super.stopCellEditing();
      myValue = null;
      return true;
    }
  }
  
  private static class MyComparator implements Comparator<ArrangementGroupingType> {

    @NotNull private final TObjectIntHashMap<ArrangementGroupingType> myWeights = new TObjectIntHashMap<ArrangementGroupingType>();

    MyComparator(@NotNull List<ArrangementGroupingRule> list) {
      int weight = 0;
      for (ArrangementGroupingRule rule : list) {
        myWeights.put(rule.getGroupingType(), weight++);
      }
    }

    @Override
    public int compare(ArrangementGroupingType t1, ArrangementGroupingType t2) {
      if (myWeights.containsKey(t1) && myWeights.containsKey(t2)) {
        return myWeights.get(t1) - myWeights.get(t2);
      }
      else if (myWeights.containsKey(t1)) {
        return -1;
      }
      else if (myWeights.containsKey(t2)) {
        return 1;
      }
      return 0;
    }
  }
}
