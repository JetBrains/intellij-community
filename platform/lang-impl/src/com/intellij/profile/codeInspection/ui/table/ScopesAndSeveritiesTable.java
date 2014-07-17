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
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.profile.codeInspection.ui.AddScopeUtil;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class ScopesAndSeveritiesTable extends JBTable {
  private final static Logger LOG = Logger.getInstance(ScopesAndSeveritiesTable.class);

  public static final HighlightSeverity MIXED_FAKE_SEVERITY = new HighlightSeverity("Mixed", -1);
  @SuppressWarnings("UnusedDeclaration")
  public static final HighlightDisplayLevel MIXED_FAKE_LEVEL = new HighlightDisplayLevel(MIXED_FAKE_SEVERITY, AllIcons.Actions.Help);

  private final static int SCOPE_ENABLED_COLUMN = 0;
  private final static int SCOPE_NAME_COLUMN = 1;
  private final static int SEVERITY_COLUMN = 2;

  public ScopesAndSeveritiesTable(final TableSettings tableSettings) {
    super(new MyTableModel(tableSettings));

    final TableColumnModel columnModel = getColumnModel();

    final TableColumn scopeEnabledColumn = columnModel.getColumn(SCOPE_ENABLED_COLUMN);
    scopeEnabledColumn.setMaxWidth(30);
    scopeEnabledColumn.setCellRenderer(new ThreeStateCheckBoxRenderer());
    scopeEnabledColumn.setCellEditor(new ThreeStateCheckBoxRenderer());

    final TableColumn severityColumn = columnModel.getColumn(SEVERITY_COLUMN);
    severityColumn.setCellRenderer(SeverityRenderer.create(tableSettings.getInspectionProfile()));
    severityColumn.setCellEditor(SeverityRenderer.create(tableSettings.getInspectionProfile()));

    setColumnSelectionAllowed(false);
    setRowSelectionAllowed(true);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        final int idx = getSelectionModel().getMinSelectionIndex();
        if (idx >= 0) {
          final ExistedScopesStatesAndNonExistNames scopeToolState = ((MyTableModel)getModel()).getScopeToolState(idx);
          final List<ScopeToolState> existedStates = scopeToolState.getExistedStates();
          if (existedStates.size() == 1) {
            tableSettings.onScopeChosen(existedStates.get(0));
          }
        }
      }
    });
    setRowSelectionInterval(0, 0);

    setStriped(true);
    setShowGrid(false);
  }

  public abstract static class TableSettings {
    private final List<InspectionConfigTreeNode> myNodes;
    private final List<String> myKeyNames;
    private final List<HighlightDisplayKey> myKeys;
    private final InspectionProfileImpl myInspectionProfile;
    private final TreeTable myTreeTable;
    private final Project myProject;

    protected TableSettings(final List<InspectionConfigTreeNode> nodes,
                            final InspectionProfileImpl inspectionProfile,
                            final TreeTable treeTable,
                            final Project project) {
      myNodes = nodes;
      myKeys = new ArrayList<HighlightDisplayKey>(myNodes.size());
      myKeyNames = new ArrayList<String>(myNodes.size());
      for(final InspectionConfigTreeNode node : nodes) {
        final HighlightDisplayKey key = node.getDefaultDescriptor().getKey();
        myKeys.add(key);
        myKeyNames.add(key.toString());
      }

      myInspectionProfile = inspectionProfile;
      myTreeTable = treeTable;
      myProject = project;
    }

    public List<HighlightDisplayKey> getKeys() {
      return myKeys;
    }

    public List<String> getKeyNames() {
      return myKeyNames;
    }

    public List<InspectionConfigTreeNode> getNodes() {
      return myNodes;
    }

    public InspectionProfileImpl getInspectionProfile() {
      return myInspectionProfile;
    }

    public TreeTable getTreeTable() {
      return myTreeTable;
    }

    public Project getProject() {
      return myProject;
    }

    protected abstract void onScopeAdded();

    protected abstract void onScopeRemoved(final int scopesCount);

    protected abstract void onScopeChosen(final @NotNull ScopeToolState scopeToolState);

    protected abstract void onChange();
  }

  @NotNull
  public static HighlightSeverity getSeverity(final List<ScopeToolState> scopeToolStates) {
    HighlightSeverity previousValue = null;
    for (final ScopeToolState scopeToolState : scopeToolStates) {
      final HighlightSeverity currentValue = scopeToolState.getLevel().getSeverity();
      if (previousValue == null) {
        previousValue = currentValue;
      } else if (!previousValue.equals(currentValue)){
        return MIXED_FAKE_SEVERITY;
      }
    }
    return previousValue;
  }

  private static class MyTableModel extends AbstractTableModel implements EditableModel {
    private final InspectionProfileImpl myInspectionProfile;
    private final List<String> myKeyNames;
    private final List<InspectionConfigTreeNode> myNodes;
    private final TreeTable myTreeTable;
    private final Project myProject;
    private final TableSettings myTableSettings;
    private final List<HighlightDisplayKey> myKeys;

    private String[] myScopeNames;

    public MyTableModel(final TableSettings tableSettings) {
      myTableSettings = tableSettings;
      myProject = tableSettings.getProject();
      myInspectionProfile = tableSettings.getInspectionProfile();
      myKeys = tableSettings.getKeys();
      myKeyNames = tableSettings.getKeyNames();
      myNodes = tableSettings.getNodes();
      myTreeTable = tableSettings.getTreeTable();
      refreshAggregatedScopes();
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
      return columnIndex != SCOPE_NAME_COLUMN;
    }

    @Override
    public int getRowCount() {
      return lastRowIndex() + 1;
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
      switch (columnIndex) {
        case SCOPE_ENABLED_COLUMN:
          return isEnabled(rowIndex);
        case SCOPE_NAME_COLUMN:
          return getScope(rowIndex).getName();
        case SEVERITY_COLUMN:
          return getSeverity(rowIndex);
        default:
          throw new IllegalArgumentException("Invalid column index " + columnIndex);
      }
    }

    private NamedScope getScope(final int rowIndex) {
      return getScopeToolState(rowIndex).getExistedStates().get(0).getScope(myProject);
    }

    @NotNull
    private HighlightSeverity getSeverity(final int rowIndex) {
      final ExistedScopesStatesAndNonExistNames existedScopesStatesAndNonExistNames = getScopeToolState(rowIndex);
      if (!existedScopesStatesAndNonExistNames.getNonExistNames().isEmpty()) {
        return MIXED_FAKE_SEVERITY;
      }
      return ScopesAndSeveritiesTable.getSeverity(existedScopesStatesAndNonExistNames.getExistedStates());
    }

    @Nullable
    private Boolean isEnabled(final int rowIndex) {
      Boolean previousValue = null;
      final ExistedScopesStatesAndNonExistNames existedScopesStatesAndNonExistNames = getScopeToolState(rowIndex);
      for (final ScopeToolState scopeToolState : existedScopesStatesAndNonExistNames.getExistedStates()) {
        final boolean currentValue = scopeToolState.isEnabled();
        if (previousValue == null) {
          previousValue = currentValue;
        } else if (!previousValue.equals(currentValue)){
          return null;
        }
      }
      if (!existedScopesStatesAndNonExistNames.getNonExistNames().isEmpty() && !Boolean.FALSE.equals(previousValue)) {
        return null;
      }
      return previousValue;
    }

    private ExistedScopesStatesAndNonExistNames getScopeToolState(final int rowIndex) {
      final List<String> nonExistNames = new SmartList<String>();
      final List<ScopeToolState> existedStates = new SmartList<ScopeToolState>();
      for (final String keyName : myKeyNames) {
        final ScopeToolState scopeToolState = getScopeToolState(keyName, rowIndex);
        if (scopeToolState != null) {
          existedStates.add(scopeToolState);
        } else {
          nonExistNames.add(keyName);
        }
      }
      return new ExistedScopesStatesAndNonExistNames(existedStates, nonExistNames);
    }

    @Nullable
    private ScopeToolState getScopeToolState(final String keyName, final int rowIndex) {
      if (rowIndex == lastRowIndex()) {
        return myInspectionProfile.getToolDefaultState(keyName, myProject);
      }
      else {
        final String scopeName = myScopeNames[rowIndex];
        final List<ScopeToolState> nonDefaultTools = myInspectionProfile.getNonDefaultTools(keyName, myProject);
        for (final ScopeToolState nonDefaultTool : nonDefaultTools) {
          if (Comparing.equal(scopeName, nonDefaultTool.getScopeName())) {
            return nonDefaultTool;
          }
        }
      }
      return null;
    }

    private void refreshAggregatedScopes() {
      final LinkedHashSet<String> scopesNames = new LinkedHashSet<String>();
      for (final String keyName : myKeyNames) {
        final List<ScopeToolState> nonDefaultTools = myInspectionProfile.getNonDefaultTools(keyName, myProject);
        for (final ScopeToolState tool : nonDefaultTools) {
          scopesNames.add(tool.getScopeName());
        }
      }
      myScopeNames = ArrayUtil.toStringArray(scopesNames);
    }

    private int lastRowIndex() {
      return myScopeNames.length;
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
        final int idx = rowIndex == lastRowIndex() ? -1 : rowIndex;
        myInspectionProfile.setErrorLevel(myKeys, level, idx, myProject);
      }
      else if (columnIndex == SCOPE_ENABLED_COLUMN) {
        final NamedScope scope = getScope(rowIndex);
        if ((Boolean)value) {
          if (rowIndex == lastRowIndex()) {
            myInspectionProfile.enableToolsByDefault(myKeyNames, myProject);
          }
          else {
            //TODO create scopes states if not exist (need scope sorting)
            myInspectionProfile.enableTools(myKeyNames, scope, myProject);
          }
        }
        else {
          if (rowIndex == lastRowIndex()) {
            myInspectionProfile.disableToolByDefault(myKeyNames, myProject);
          }
          else {
            myInspectionProfile.disableTools(myKeyNames, scope, myProject);
          }
        }
      }
      myTableSettings.onChange();
    }

    @Override
    public void removeRow(final int idx) {
      if (idx != lastRowIndex()) {
        myInspectionProfile.removeScopes(myKeyNames, getScope(idx), myProject);
        refreshAggregatedScopes();
        myTableSettings.onScopeRemoved(getRowCount());
      }
    }

    @Override
    public void addRow() {
      AddScopeUtil.performAddScope(myTreeTable, myProject, myInspectionProfile, myNodes);
      myTableSettings.onScopeAdded();
      refreshAggregatedScopes();
    }

    @Override
    public void exchangeRows(final int oldIndex, final int newIndex) {
    }

    @Override
    public boolean canExchangeRows(final int oldIndex, final int newIndex) {
      return false;
    }
  }

  private static class ExistedScopesStatesAndNonExistNames {

    private final List<ScopeToolState> myExistedStates;
    private final List<String> myNonExistNames;

    public ExistedScopesStatesAndNonExistNames(final List<ScopeToolState> existedStates, final List<String> nonExistNames) {
      myExistedStates = existedStates;
      myNonExistNames = nonExistNames;
    }

    public List<ScopeToolState> getExistedStates() {
      return myExistedStates;
    }

    public List<String> getNonExistNames() {
      return myNonExistNames;
    }
  }
}