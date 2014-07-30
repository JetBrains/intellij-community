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
package com.intellij.profile.codeInspection.ui.inspectionsTree;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.profile.codeInspection.ui.InspectionsAggregationUtil;
import com.intellij.profile.codeInspection.ui.table.ScopesAndSeveritiesTable;
import com.intellij.profile.codeInspection.ui.table.ThreeStateCheckBoxRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class InspectionsConfigTreeTable extends TreeTable {
  private final static Logger LOG = Logger.getInstance(InspectionsConfigTreeTable.class);

  private final static int TREE_COLUMN = 0;
  private final static int SEVERITIES_COLUMN = 1;
  private final static int IS_ENABLED_COLUMN = 2;

  public InspectionsConfigTreeTable(final InspectionsConfigTreeTableSettings settings) {
    super(new InspectionsConfigTreeTableModel(settings));

    final TableColumn severitiesColumn = getColumnModel().getColumn(SEVERITIES_COLUMN);
    severitiesColumn.setMaxWidth(20);

    final TableColumn isEnabledColumn = getColumnModel().getColumn(IS_ENABLED_COLUMN);
    isEnabledColumn.setMaxWidth(20);
    isEnabledColumn.setCellRenderer(new ThreeStateCheckBoxRenderer(false));
    isEnabledColumn.setCellEditor(new ThreeStateCheckBoxRenderer(true));

    addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(final MouseEvent e) {
        final Point point = e.getPoint();
        final int column = columnAtPoint(point);
        if (column != SEVERITIES_COLUMN) {
          return;
        }
        final int row = rowAtPoint(point);
        final Object maybeIcon = getModel().getValueAt(row, column);
        if (maybeIcon instanceof MultiScopeSeverityIcon) {
          final LinkedHashMap<String, HighlightSeverity> scopeToAverageSeverityMap =
            ((MultiScopeSeverityIcon)maybeIcon).getScopeToAverageSeverityMap();
          IdeTooltipManager.getInstance().show(
            new IdeTooltip(InspectionsConfigTreeTable.this, point, new ScopesAndSeveritiesHintTable(scopeToAverageSeverityMap)), false);
        }
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        final TreePath path = getTree().getPathForRow(getTree().getLeadSelectionRow());
        if (path != null) {
          final InspectionConfigTreeNode node = (InspectionConfigTreeNode)path.getLastPathComponent();
          if (node.isLeaf()) {
            swapInspectionEnableState();
          }
        }
        return true;
      }
    }.installOn(this);

    registerKeyboardAction(new ActionListener() {
                             public void actionPerformed(ActionEvent e) {
                               swapInspectionEnableState();
                               updateUI();
                             }
                           }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);

    getEmptyText().setText("No enabled inspections available");
  }

  private void swapInspectionEnableState() {
    for (int selectedRow : getSelectedRows()) {
      final Object value = getValueAt(selectedRow, IS_ENABLED_COLUMN);
      final boolean newValue = !Boolean.TRUE.equals(value);
      setValueAt(newValue, selectedRow, IS_ENABLED_COLUMN);
    }
  }

  public abstract static class InspectionsConfigTreeTableSettings {
    private final TreeNode myRoot;
    private final Project myProject;

    public InspectionsConfigTreeTableSettings(final TreeNode root, final Project project) {
      myRoot = root;
      myProject = project;
    }

    public TreeNode getRoot() {
      return myRoot;
    }

    public Project getProject() {
      return myProject;
    }

    protected abstract InspectionProfileImpl getInspectionProfile();

    protected abstract void onChanged(InspectionConfigTreeNode node);
  }

  private static class InspectionsConfigTreeTableModel extends DefaultTreeModel implements TreeTableModel {

    private final InspectionsConfigTreeTableSettings mySettings;
    private TreeTable myTreeTable;

    public InspectionsConfigTreeTableModel(final InspectionsConfigTreeTableSettings settings) {
      super(settings.getRoot());
      mySettings = settings;
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Nullable
    @Override
    public String getColumnName(final int column) {
      return null;
    }

    @Override
    public Class getColumnClass(final int column) {
      switch (column) {
        case TREE_COLUMN:
          return TreeTableModel.class;
        case SEVERITIES_COLUMN:
          return Icon.class;
        case IS_ENABLED_COLUMN:
          return Boolean.class;
      }
      throw new IllegalArgumentException();
    }

    @Nullable
    @Override
    public Object getValueAt(final Object node, final int column) {
      if (column == TREE_COLUMN) {
        return null;
      }
      final InspectionConfigTreeNode treeNode = (InspectionConfigTreeNode)node;
      final List<HighlightDisplayKey> inspectionsKeys = InspectionsAggregationUtil.getInspectionsKeys(treeNode);
      if (column == SEVERITIES_COLUMN) {
        final MultiColoredHighlightSeverityIconSink sink = new MultiColoredHighlightSeverityIconSink();
        for (final HighlightDisplayKey selectedInspectionsNode : inspectionsKeys) {
          final String toolId = selectedInspectionsNode.toString();
          if (mySettings.getInspectionProfile().getTools(toolId, mySettings.getProject()).isEnabled()) {
            sink.put(mySettings.getInspectionProfile().getToolDefaultState(toolId, mySettings.getProject()),
                     mySettings.getInspectionProfile().getNonDefaultTools(toolId, mySettings.getProject()));
          }
        }
        return sink.constructIcon(mySettings.getInspectionProfile());
      } else if (column == IS_ENABLED_COLUMN) {
        return isEnabled(inspectionsKeys);
      }
      throw new IllegalArgumentException();
    }

    @Nullable
    private Boolean isEnabled(final List<HighlightDisplayKey> selectedInspectionsNodes) {
      Boolean isPreviousEnabled = null;
      for (final HighlightDisplayKey key : selectedInspectionsNodes) {
        final boolean enabled = mySettings.getInspectionProfile().getTools(key.toString(), mySettings.getProject()).isEnabled();
        if (isPreviousEnabled == null) {
          isPreviousEnabled = enabled;
        } else if (!isPreviousEnabled.equals(enabled)) {
          return null;
        }
      }
      return isPreviousEnabled;
    }

    @Override
    public boolean isCellEditable(final Object node, final int column) {
      return column == IS_ENABLED_COLUMN;
    }

    @Override
    public void setValueAt(final Object aValue, final Object node, final int column) {
      LOG.assertTrue(column == IS_ENABLED_COLUMN);
      LOG.assertTrue(aValue != null);
      final boolean doEnable = (Boolean) aValue;
      for (final InspectionConfigTreeNode aNode : InspectionsAggregationUtil.getInspectionsNodes((InspectionConfigTreeNode) node)) {
        final String toolId = aNode.getKey().toString();
        if (doEnable) {
          mySettings.getInspectionProfile().enableTool(toolId, mySettings.getProject());
        } else {
          mySettings.getInspectionProfile().disableTool(toolId, mySettings.getProject());
        }
        aNode.dropCache();
        mySettings.onChanged(aNode);
      }
      if (myTreeTable != null) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          public void run() {
            ((AbstractTableModel)myTreeTable.getModel()).fireTableDataChanged();
          }
        });
      }
    }

    @Override
    public void setTree(final JTree tree) {
      myTreeTable = ((TreeTableTree)tree).getTreeTable();
    }
  }

  private static class MultiColoredHighlightSeverityIconSink {

    private final Map<String, HighlightSeverity> myScopeToAverageSeverityMap = new HashMap<String, HighlightSeverity>();

    private String myDefaultScopeName;
    private boolean myIsFirst = true;

    public Icon constructIcon(final InspectionProfileImpl inspectionProfile) {
      if (myScopeToAverageSeverityMap.isEmpty()) {
        return null;
      }
      return !allScopesHasMixedSeverity()
             ? new MultiScopeSeverityIcon(myScopeToAverageSeverityMap, myDefaultScopeName, inspectionProfile)
             : ScopesAndSeveritiesTable.MIXED_FAKE_LEVEL.getIcon();
    }

    private boolean allScopesHasMixedSeverity() {
      for (final Map.Entry<String, HighlightSeverity> e : myScopeToAverageSeverityMap.entrySet()) {
        if (!ScopesAndSeveritiesTable.MIXED_FAKE_SEVERITY.equals(e.getValue())) {
          return false;
        }
      }
      return true;
    }

    public void put(final ScopeToolState defaultState, final Collection<ScopeToolState> nonDefault) {
      putOne(defaultState);
      if (myDefaultScopeName == null) {
        myDefaultScopeName = defaultState.getScopeName();
      }
      for (final ScopeToolState scopeToolState : nonDefault) {
        putOne(scopeToolState);
      }
      if (myIsFirst) {
        myIsFirst = false;
      }
    }

    private void putOne(final ScopeToolState state) {
      final Icon icon = state.getLevel().getIcon();
      final String scopeName = state.getScopeName();
      if (icon instanceof HighlightDisplayLevel.SingleColorIconWithMask) {
        if (myIsFirst) {
          myScopeToAverageSeverityMap.put(scopeName, state.getLevel().getSeverity());
        } else {
          final HighlightSeverity severity = myScopeToAverageSeverityMap.get(scopeName);
          if (!ScopesAndSeveritiesTable.MIXED_FAKE_SEVERITY.equals(severity) && !Comparing.equal(severity, state.getLevel().getSeverity())) {
            myScopeToAverageSeverityMap.put(scopeName, ScopesAndSeveritiesTable.MIXED_FAKE_SEVERITY);
          }
        }
      } else if (!ScopesAndSeveritiesTable.MIXED_FAKE_SEVERITY.equals(myScopeToAverageSeverityMap.get(scopeName))) {
        myScopeToAverageSeverityMap.put(scopeName, ScopesAndSeveritiesTable.MIXED_FAKE_SEVERITY);
      }
    }
  }
}
