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
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.ide.DataManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.profile.codeInspection.ui.ScopeOrderComparator;
import com.intellij.profile.codeInspection.ui.ScopesChooser;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class ScopesAndSeveritiesTable extends JBTable {
  private final static Logger LOG = Logger.getInstance(ScopesAndSeveritiesTable.class);

  public static final HighlightSeverity MIXED_FAKE_SEVERITY = new HighlightSeverity("Mixed", -1);
  @SuppressWarnings("UnusedDeclaration")
  public static final HighlightDisplayLevel MIXED_FAKE_LEVEL = new HighlightDisplayLevel(MIXED_FAKE_SEVERITY, EmptyIcon.create(12));

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
    severityColumn.setCellRenderer(SeverityRenderer.create(tableSettings.getInspectionProfile(), null));
    severityColumn.setCellEditor(SeverityRenderer.create(tableSettings.getInspectionProfile(), () -> tableSettings.onSettingsChanged()));

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
          if (existedStates.size() == 1 && scopeToolState.getNonExistNames().isEmpty()) {
            tableSettings.onScopeChosen(existedStates.get(0));
          }
        }
      }
    });
    setRowSelectionInterval(0, 0);

    setStriped(true);
    setShowGrid(false);

    ((MyTableModel)getModel()).setTable(this);
  }

  public abstract static class TableSettings {
    private final List<InspectionConfigTreeNode> myNodes;
    private final List<String> myKeyNames;
    private final List<HighlightDisplayKey> myKeys;
    private final InspectionProfileImpl myInspectionProfile;
    private final Project myProject;

    protected TableSettings(final List<InspectionConfigTreeNode> nodes,
                            final InspectionProfileImpl inspectionProfile,
                            final Project project) {
      myNodes = nodes;
      myKeys = new ArrayList<>(myNodes.size());
      myKeyNames = new ArrayList<>(myNodes.size());
      for(final InspectionConfigTreeNode node : nodes) {
        final HighlightDisplayKey key = node.getDefaultDescriptor().getKey();
        myKeys.add(key);
        myKeyNames.add(key.toString());
      }

      myInspectionProfile = inspectionProfile;
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

    public Project getProject() {
      return myProject;
    }

    protected abstract void onScopeAdded();

    protected abstract void onScopesOrderChanged();

    protected abstract void onScopeRemoved(final int scopesCount);

    protected abstract void onScopeChosen(final @NotNull ScopeToolState scopeToolState);

    protected abstract void onSettingsChanged();
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
    private final Project myProject;
    private final TableSettings myTableSettings;
    private final List<HighlightDisplayKey> myKeys;
    private final Comparator<String> myScopeComparator;

    private JTable myTable;
    private String[] myScopeNames;

    public MyTableModel(final TableSettings tableSettings) {
      myTableSettings = tableSettings;
      myProject = tableSettings.getProject();
      myInspectionProfile = tableSettings.getInspectionProfile();
      myKeys = tableSettings.getKeys();
      myKeyNames = tableSettings.getKeyNames();
      myScopeComparator = new ScopeOrderComparator(myInspectionProfile);
      refreshAggregatedScopes();
    }

    public void setTable(JTable table) {
      myTable = table;
    }

    @Override
    public boolean isCellEditable(final int rowIndex, final int columnIndex) {
      if (columnIndex == SCOPE_NAME_COLUMN) {
        return false;
      } else if (columnIndex == SCOPE_ENABLED_COLUMN) {
        return true;
      }
      assert columnIndex == SEVERITY_COLUMN;

      final SeverityState state = getSeverityState(rowIndex);
      if (state.isDisabled()) {
        return false;
      }

      final ExistedScopesStatesAndNonExistNames scopeToolState = getScopeToolState(rowIndex);
      return scopeToolState.getNonExistNames().isEmpty();
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
        return SeverityState.class;
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
          return rowIndex == lastRowIndex() ? "Everywhere else" : getScopeName(rowIndex);
        case SEVERITY_COLUMN:
          return getSeverityState(rowIndex);
        default:
          throw new IllegalArgumentException("Invalid column index " + columnIndex);
      }
    }

    private NamedScope getScope(final int rowIndex) {
      return getScopeToolState(rowIndex).getExistedStates().get(0).getScope(myProject);
    }

    private String getScopeName(final int rowIndex) {
      return getScopeToolState(rowIndex).getExistedStates().get(0).getScopeName();
    }

    @NotNull
    private SeverityState getSeverityState(final int rowIndex) {
      boolean disabled = Boolean.FALSE.equals(isEnabled(rowIndex));
      final ExistedScopesStatesAndNonExistNames existedScopesStatesAndNonExistNames = getScopeToolState(rowIndex);
      if (!existedScopesStatesAndNonExistNames.getNonExistNames().isEmpty()) {
        return new SeverityState(MIXED_FAKE_SEVERITY, false, disabled);
      }
      return new SeverityState(getSeverity(existedScopesStatesAndNonExistNames.getExistedStates()), !disabled, disabled);
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
      final List<String> nonExistNames = new SmartList<>();
      final List<ScopeToolState> existedStates = new SmartList<>();
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
      final LinkedHashSet<String> scopesNames = new LinkedHashSet<>();
      for (final String keyName : myKeyNames) {
        final List<ScopeToolState> nonDefaultTools = myInspectionProfile.getNonDefaultTools(keyName, myProject);
        for (final ScopeToolState tool : nonDefaultTools) {
          scopesNames.add(tool.getScopeName());
        }
      }
      myScopeNames = ArrayUtil.toStringArray(scopesNames);
      Arrays.sort(myScopeNames, myScopeComparator);
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
        final SeverityState severityState = (SeverityState)value;
        final HighlightDisplayLevel level = HighlightDisplayLevel.find(severityState.getSeverity().getName());
        if (level == null) {
          LOG.error("no display level found for name " + severityState.getSeverity().getName());
          return;
        }
        final String scopeName = rowIndex == lastRowIndex() ? null : getScopeName(rowIndex);
        myInspectionProfile.setErrorLevel(myKeys, level, scopeName, myProject);
      }
      else if (columnIndex == SCOPE_ENABLED_COLUMN) {
        final NamedScope scope = getScope(rowIndex);
        if (scope == null) {
          return;
        }
        if ((Boolean)value) {
          for (final String keyName : myKeyNames) {
            myInspectionProfile.enableTool(keyName, myProject);
          }
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
        if (myKeyNames.size() == 1) {
          final String keyName = ContainerUtil.getFirstItem(myKeyNames);
          final ScopeToolState state = getScopeToolState(keyName, rowIndex);
          myTableSettings.onScopeChosen(state);
        }
      }
      myTableSettings.onSettingsChanged();
    }

    @Override
    public void removeRow(final int idx) {
      if (idx != lastRowIndex()) {
        myInspectionProfile.removeScopes(myKeyNames, getScopeName(idx), myProject);
        refreshAggregatedScopes();
        myTableSettings.onScopeRemoved(getRowCount());
      }
    }

    @Override
    public void addRow() {
      final List<Descriptor> descriptors = ContainerUtil.map(myTableSettings.getNodes(), inspectionConfigTreeNode -> inspectionConfigTreeNode.getDefaultDescriptor());
      final ScopesChooser scopesChooser = new ScopesChooser(descriptors, myInspectionProfile, myProject, myScopeNames) {
        @Override
        protected void onScopeAdded() {
          myTableSettings.onScopeAdded();
          refreshAggregatedScopes();
        }

        @Override
        protected void onScopesOrderChanged() {
          myTableSettings.onScopesOrderChanged();
        }
      };
      DataContext dataContext = DataManager.getInstance().getDataContext(myTable);
      final ListPopup popup = JBPopupFactory.getInstance()
        .createActionGroupPopup(ScopesChooser.TITLE, scopesChooser.createPopupActionGroup(myTable), dataContext,
                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
      final RelativePoint point = new RelativePoint(myTable, new Point(myTable.getWidth() - popup.getContent().getPreferredSize().width, 0));
      popup.show(point);
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