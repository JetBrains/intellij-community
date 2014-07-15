/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui.table;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ui.AddScopeUtil;
import com.intellij.profile.codeInspection.ui.InspectionConfigTreeNode;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.util.EventObject;

/**
 * @author Dmitry Batkovich
 */
public class ScopesAndSeveritiesTable extends JBTable {
  private final static Logger LOG = Logger.getInstance(ScopesAndSeveritiesTable.class);

  private final static int SCOPE_ENABLED_COLUMN = 0;
  private final static int SCOPE_NAME_COLUMN = 1;
  private final static int SEVERITY_COLUMN = 2;
  private final TableSettings myTableSettings;

  public ScopesAndSeveritiesTable(final TableSettings tableSettings) {
    super(new MyTableModel(tableSettings));
    myTableSettings = tableSettings;

    final TableColumnModel columnModel = getColumnModel();

    columnModel.getColumn(SCOPE_ENABLED_COLUMN).setMaxWidth(30);

    final TableColumn severityColumn = columnModel.getColumn(SEVERITY_COLUMN);
    severityColumn.setCellRenderer(SeverityRenderer.create(tableSettings.getInspectionProfile()));
    severityColumn.setCellEditor(SeverityRenderer.create(tableSettings.getInspectionProfile()));

    setColumnSelectionAllowed(false);
    setRowSelectionAllowed(true);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    setRowSelectionInterval(0, 0);

    setStriped(true);
    setShowGrid(false);
  }

  @Override
  public boolean editCellAt(final int row, final int column, final EventObject e) {
    final boolean res = super.editCellAt(row, column, e);
    if (row >= 0) {
      myTableSettings.onScopeChosen(((MyTableModel)getModel()).getScopeToolState(row));
    }
    return res;
  }

  public abstract static class TableSettings {
    private final InspectionConfigTreeNode myNode;
    private final InspectionProfileImpl myInspectionProfile;
    private final Tree myTree;
    private final Project myProject;
    private final HighlightDisplayKey myKey;
    private final String myKeyName;

    protected TableSettings(final InspectionConfigTreeNode node,
                            final InspectionProfileImpl inspectionProfile,
                            final Tree tree,
                            final Project project) {
      myNode = node;
      myKey = node.getDefaultDescriptor().getKey();
      myKeyName = myKey.toString();
      myInspectionProfile = inspectionProfile;
      myTree = tree;
      myProject = project;
    }

    public HighlightDisplayKey getKey() {
      return myKey;
    }

    public String getKeyName() {
      return myKeyName;
    }

    public InspectionConfigTreeNode getNode() {
      return myNode;
    }

    public InspectionProfileImpl getInspectionProfile() {
      return myInspectionProfile;
    }

    public Tree getTree() {
      return myTree;
    }

    public Project getProject() {
      return myProject;
    }

    protected abstract void onScopeAdded();

    protected abstract void onScopeRemoved(final int scopesCount);

    protected abstract void onScopeChosen(final @NotNull ScopeToolState scopeToolState);
  }

  private static class MyTableModel extends AbstractTableModel implements EditableModel {
    private final InspectionProfileImpl myInspectionProfile;
    private final String myKeyName;
    private final InspectionConfigTreeNode myNode;
    private final Tree myTree;
    private final Project myProject;
    private final TableSettings myTableSettings;
    private final HighlightDisplayKey myKey;

    public MyTableModel(final TableSettings tableSettings) {
      myTableSettings = tableSettings;
      myProject = tableSettings.getProject();
      myInspectionProfile = tableSettings.getInspectionProfile();
      myKey = tableSettings.getKey();
      myKeyName = tableSettings.getKeyName();
      myNode = tableSettings.getNode();
      myTree = tableSettings.getTree();
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
      return columnIndex != SCOPE_NAME_COLUMN;
    }

    @Override
    public int getRowCount() {
      return myInspectionProfile.getNonDefaultTools(myKeyName, myProject).size() + 1;
    }

    @Nullable
    @Override
    public String getColumnName(final int column) {
      return null;
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public Class<?> getColumnClass(final int columnIndex) {
      if (SCOPE_ENABLED_COLUMN == columnIndex) {
        return Boolean.class;
      }
      if (SCOPE_NAME_COLUMN == columnIndex) {
        return String.class;
      }
      if (SEVERITY_COLUMN == columnIndex) {
        return HighlightSeverity.class;
      }
      throw new IllegalArgumentException();
    }

    @Override
    public Object getValueAt(final int rowIndex, final int columnIndex) {
      if (rowIndex < 0) {
        return null;
      }
      final ScopeToolState state = getScopeToolState(rowIndex);
      switch (columnIndex) {
        case SCOPE_ENABLED_COLUMN:
          return state.isEnabled();
        case SCOPE_NAME_COLUMN:
          return state.getScopeName();
        case SEVERITY_COLUMN:
          return state.getLevel().getSeverity();
        default:
          throw new IllegalArgumentException("Invalid column index " + columnIndex);
      }
    }

    private ScopeToolState getScopeToolState(final int rowIndex) {
      return rowIndex == 0
             ? myInspectionProfile.getToolDefaultState(myKeyName, myProject)
             : myInspectionProfile.getNonDefaultTools(myKeyName, myProject).get(rowIndex - 1);
    }

    @Override
    public void setValueAt(final Object value, final int rowIndex, final int columnIndex) {
      if (value == null) {
        return;
      }
      if (columnIndex == SEVERITY_COLUMN) {
        final HighlightDisplayLevel level = HighlightDisplayLevel.find(((HighlightSeverity)value).getName());
        if (level == null) {
          LOG.error("no display level found for name " + ((HighlightSeverity)value).getName());
          return;
        }
        myInspectionProfile.setErrorLevel(myKey, level, rowIndex - 1, myProject);
      }
      else if (columnIndex == SCOPE_ENABLED_COLUMN) {
        if ((Boolean)value) {
          if (rowIndex == 0) {
            myInspectionProfile.enableToolByDefault(myKeyName, myProject);
          }
          else {
            myInspectionProfile.enableTool(myKeyName, getScopeToolState(rowIndex).getScope(myProject), myProject);
          }
        }
        else {
          if (rowIndex == 0) {
            myInspectionProfile.disableToolByDefault(myKeyName, myProject);
          }
          else {
            myInspectionProfile.disableTool(myKeyName, getScopeToolState(rowIndex).getScope(myProject), myProject);
          }
        }
      }
    }

    @Override
    public void removeRow(final int idx) {
      if (idx > 0) {
        myInspectionProfile.removeScope(myKeyName, getScopeToolState(idx), myProject);
        myTableSettings.onScopeRemoved(getRowCount());
      }
    }

    @Override
    public void addRow() {
      AddScopeUtil.performAddScope(myTree, myProject, myInspectionProfile, myNode);
      myTableSettings.onScopeAdded();
    }

    @Override
    public void exchangeRows(final int oldIndex, final int newIndex) {
      myInspectionProfile.moveScope(myKeyName, oldIndex - 1, newIndex - oldIndex, myProject);
    }

    @Override
    public boolean canExchangeRows(final int oldIndex, final int newIndex) {
      return getRowCount() > 2 && oldIndex != 0 && newIndex != 0;
    }
  }
}