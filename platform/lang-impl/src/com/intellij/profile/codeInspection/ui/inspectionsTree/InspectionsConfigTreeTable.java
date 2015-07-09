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
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ui.InspectionsAggregationUtil;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.profile.codeInspection.ui.ToolDescriptors;
import com.intellij.profile.codeInspection.ui.table.ScopesAndSeveritiesTable;
import com.intellij.profile.codeInspection.ui.table.ThreeStateCheckBoxRenderer;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.Alarm;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.IconTableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Transferable;
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

  public static int getAdditionalPadding() {
    return SystemInfo.isMac ? 10 : 0;
  }

  public static InspectionsConfigTreeTable create(final InspectionsConfigTreeTableSettings settings, Disposable parentDisposable) {
    return new InspectionsConfigTreeTable(new InspectionsConfigTreeTableModel(settings, parentDisposable));
  }

  public InspectionsConfigTreeTable(final InspectionsConfigTreeTableModel model) {
    super(model);

    final TableColumn severitiesColumn = getColumnModel().getColumn(SEVERITIES_COLUMN);
    severitiesColumn.setCellRenderer(new IconTableCellRenderer<Icon>() {

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
        Component component = super.getTableCellRendererComponent(table, value, false, focus, row, column);
        Color bg = selected ? ((focus || table.hasFocus()) ? table.getSelectionBackground() : UIUtil.getTreeUnfocusedSelectionBackground())
                            : table.getBackground();
        component.setBackground(bg);
        ((JLabel) component).setText("");
        return component;
      }

      @Nullable
      @Override
      protected Icon getIcon(@NotNull Icon value, JTable table, int row) {
        return value;
      }
    });
    severitiesColumn.setMaxWidth(20);

    final TableColumn isEnabledColumn = getColumnModel().getColumn(IS_ENABLED_COLUMN);
    isEnabledColumn.setMaxWidth(20 + getAdditionalPadding());
    isEnabledColumn.setCellRenderer(new ThreeStateCheckBoxRenderer());
    isEnabledColumn.setCellEditor(new ThreeStateCheckBoxRenderer());

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
          final MultiScopeSeverityIcon icon = (MultiScopeSeverityIcon)maybeIcon;
          final LinkedHashMap<String, HighlightDisplayLevel> scopeToAverageSeverityMap =
            icon.getScopeToAverageSeverityMap();
          final JComponent component;
          if (scopeToAverageSeverityMap.size() == 1 &&
              icon.getDefaultScopeName().equals(ContainerUtil.getFirstItem(scopeToAverageSeverityMap.keySet()))) {
            final HighlightDisplayLevel level = ContainerUtil.getFirstItem(scopeToAverageSeverityMap.values());
            final JLabel label = new JLabel();
            label.setIcon(level.getIcon());
            label.setText(SingleInspectionProfilePanel.renderSeverity(level.getSeverity()));
            component = label;
          } else {
            component = new ScopesAndSeveritiesHintTable(scopeToAverageSeverityMap, icon.getDefaultScopeName());
          }
          IdeTooltipManager.getInstance().show(
            new IdeTooltip(InspectionsConfigTreeTable.this, point, component), false);
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
            model.swapInspectionEnableState();
          }
        }
        return true;
      }
    }.installOn(this);

    setTransferHandler(new TransferHandler() {
      @Nullable
      @Override
      protected Transferable createTransferable(JComponent c) {
        final TreePath path = getTree().getPathForRow(getTree().getLeadSelectionRow());
        if (path != null) {
          return new TextTransferable(StringUtil.join(ContainerUtil.mapNotNull(path.getPath(), new NullableFunction<Object, String>() {
            @Nullable
            @Override
            public String fun(Object o) {
              return o == path.getPath()[0] ? null : o.toString();
            }
          }), " | "));
        }
        return null;
      }

      @Override
      public int getSourceActions(JComponent c) {
        return COPY;
      }
    });

    registerKeyboardAction(new ActionListener() {
                             public void actionPerformed(ActionEvent e) {
                               model.swapInspectionEnableState();
                               updateUI();
                             }
                           }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);

    getEmptyText().setText("No enabled inspections available");
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

    public abstract void updateRightPanel();
  }

  private static class InspectionsConfigTreeTableModel extends DefaultTreeModel implements TreeTableModel {

    private final InspectionsConfigTreeTableSettings mySettings;
    private final Runnable myUpdateRunnable;
    private TreeTable myTreeTable;

    private Alarm myUpdateAlarm;

    public InspectionsConfigTreeTableModel(final InspectionsConfigTreeTableSettings settings, Disposable parentDisposable) {
      super(settings.getRoot());
      mySettings = settings;
      myUpdateRunnable = new Runnable() {
        public void run() {
          settings.updateRightPanel();
          ((AbstractTableModel)myTreeTable.getModel()).fireTableDataChanged();
        }
      };
      myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable);
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
        final ToolsImpl tools = mySettings.getInspectionProfile().getTools(key.toString(), mySettings.getProject());
        for (final ScopeToolState state : tools.getTools()) {
          final boolean enabled = state.isEnabled();
          if (isPreviousEnabled == null) {
            isPreviousEnabled = enabled;
          } else if (!isPreviousEnabled.equals(enabled)) {
            return null;
          }
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
      if (aValue == null) {
        return;
      }
      final boolean doEnable = (Boolean) aValue;
      final InspectionProfileImpl profile = mySettings.getInspectionProfile();
      for (final InspectionConfigTreeNode aNode : InspectionsAggregationUtil.getInspectionsNodes((InspectionConfigTreeNode)node)) {
        setToolEnabled(doEnable, profile, aNode.getKey());
        aNode.dropCache();
        mySettings.onChanged(aNode);
      }
      updateRightPanel();
    }

    public void swapInspectionEnableState() {
      LOG.assertTrue(myTreeTable != null);

      Boolean state = null;
      final HashSet<HighlightDisplayKey> tools = new HashSet<HighlightDisplayKey>();
      final List<InspectionConfigTreeNode> nodes = new ArrayList<InspectionConfigTreeNode>();

      for (TreePath selectionPath : myTreeTable.getTree().getSelectionPaths()) {
        final InspectionConfigTreeNode node = (InspectionConfigTreeNode)selectionPath.getLastPathComponent();
        collectInspectionFromNodes(node, tools, nodes);
      }

      final int[] selectedRows = myTreeTable.getSelectedRows();
      for (int selectedRow : selectedRows) {
        final Boolean value = (Boolean)myTreeTable.getValueAt(selectedRow, IS_ENABLED_COLUMN);
        if (state == null) {
          state = value;
        }
        else if (!state.equals(value)) {
          state = null;
          break;
        }
      }
      final boolean newState = !Boolean.TRUE.equals(state);

      final InspectionProfileImpl profile = mySettings.getInspectionProfile();
      for (HighlightDisplayKey tool : tools) {
        setToolEnabled(newState, profile, tool);
      }

      for (InspectionConfigTreeNode node : nodes) {
        node.dropCache();
        mySettings.onChanged(node);
      }

      updateRightPanel();
    }

    private void updateRightPanel() {
      if (myTreeTable != null) {
        if (!myUpdateAlarm.isDisposed()) {
          myUpdateAlarm.cancelAllRequests();
          myUpdateAlarm.addRequest(myUpdateRunnable, 10, ModalityState.stateForComponent(myTreeTable));
        }
      }
    }

    private void setToolEnabled(boolean newState, InspectionProfileImpl profile, HighlightDisplayKey tool) {
      final String toolId = tool.toString();
      if (newState) {
        profile.enableTool(toolId, mySettings.getProject());
      }
      else {
        profile.disableTool(toolId, mySettings.getProject());
      }
      for (ScopeToolState scopeToolState : profile.getTools(toolId, mySettings.getProject()).getTools()) {
        scopeToolState.setEnabled(newState);
      }
    }

    private static void collectInspectionFromNodes(final InspectionConfigTreeNode node,
                                                   final Set<HighlightDisplayKey> tools,
                                                   final List<InspectionConfigTreeNode> nodes) {
      if (node == null) {
        return;
      }
      nodes.add(node);

      final ToolDescriptors descriptors = node.getDescriptors();
      if (descriptors == null) {
        for (int i = 0; i < node.getChildCount(); i++) {
          collectInspectionFromNodes((InspectionConfigTreeNode)node.getChildAt(i), tools, nodes);
        }
      } else {
        final HighlightDisplayKey key = descriptors.getDefaultDescriptor().getKey();
        tools.add(key);
      }
    }

    @Override
    public void setTree(final JTree tree) {
      myTreeTable = ((TreeTableTree)tree).getTreeTable();
    }
  }

  private static class SeverityAndOccurrences {
    private HighlightSeverity myPrimarySeverity;
    private final Map<String, HighlightSeverity> myOccurrences = new HashMap<String, HighlightSeverity>();

    public void setSeverityToMixed() {
      myPrimarySeverity = ScopesAndSeveritiesTable.MIXED_FAKE_SEVERITY;
    }

    public SeverityAndOccurrences incOccurrences(final String toolName, final HighlightSeverity severity) {
      if (myPrimarySeverity == null) {
        myPrimarySeverity = severity;
      } else if (!Comparing.equal(severity, myPrimarySeverity)) {
        myPrimarySeverity = ScopesAndSeveritiesTable.MIXED_FAKE_SEVERITY;
      }
      myOccurrences.put(toolName, severity);
      return this;
    }

    public HighlightSeverity getPrimarySeverity() {
      return myPrimarySeverity;
    }

    public int getOccurrencesSize() {
      return myOccurrences.size();
    }

    public Map<String, HighlightSeverity> getOccurrences() {
      return myOccurrences;
    }
  }

  private static class MultiColoredHighlightSeverityIconSink {


    private final Map<String, SeverityAndOccurrences> myScopeToAverageSeverityMap = new HashMap<String, SeverityAndOccurrences>();

    private String myDefaultScopeName;

    public Icon constructIcon(final InspectionProfileImpl inspectionProfile) {
      final Map<String, HighlightSeverity> computedSeverities = computeSeverities(inspectionProfile);

      if (computedSeverities == null) {
        return null;
      }

      boolean allScopesHasMixedSeverity = true;
      for (HighlightSeverity severity : computedSeverities.values()) {
        if (!severity.equals(ScopesAndSeveritiesTable.MIXED_FAKE_SEVERITY)) {
          allScopesHasMixedSeverity = false;
          break;
        }
      }
      return allScopesHasMixedSeverity
             ? ScopesAndSeveritiesTable.MIXED_FAKE_LEVEL.getIcon()
             : new MultiScopeSeverityIcon(computedSeverities, myDefaultScopeName, inspectionProfile);
    }

    @Nullable
    private Map<String, HighlightSeverity> computeSeverities(final InspectionProfileImpl inspectionProfile) {
      if (myScopeToAverageSeverityMap.isEmpty()) {
        return null;
      }
      final Map<String, HighlightSeverity> result = new HashMap<String, HighlightSeverity>();
      final Map.Entry<String, SeverityAndOccurrences> entry = ContainerUtil.getFirstItem(myScopeToAverageSeverityMap.entrySet());
      result.put(entry.getKey(), entry.getValue().getPrimarySeverity());
      if (myScopeToAverageSeverityMap.size() == 1) {
        return result;
      }

      final SeverityAndOccurrences defaultSeveritiesAndOccurrences = myScopeToAverageSeverityMap.get(myDefaultScopeName);
      if (defaultSeveritiesAndOccurrences == null) {
        for (Map.Entry<String, SeverityAndOccurrences> e: myScopeToAverageSeverityMap.entrySet()) {
          final HighlightSeverity primarySeverity = e.getValue().getPrimarySeverity();
          if (primarySeverity != null) {
            result.put(e.getKey(), primarySeverity);
          }
        }
        return result;
      }
      final int allInspectionsCount = defaultSeveritiesAndOccurrences.getOccurrencesSize();
      final Map<String, HighlightSeverity> allScopes = defaultSeveritiesAndOccurrences.getOccurrences();
      for (String currentScope : myScopeToAverageSeverityMap.keySet()) {
        final SeverityAndOccurrences currentSeverityAndOccurrences = myScopeToAverageSeverityMap.get(currentScope);
        if (currentSeverityAndOccurrences == null) {
          continue;
        }
        final HighlightSeverity currentSeverity = currentSeverityAndOccurrences.getPrimarySeverity();
        if (currentSeverity == ScopesAndSeveritiesTable.MIXED_FAKE_SEVERITY ||
            currentSeverityAndOccurrences.getOccurrencesSize() == allInspectionsCount ||
            myDefaultScopeName.equals(currentScope)) {
          result.put(currentScope, currentSeverity);
        }
        else {
          Set<String> toolsToCheck = ContainerUtil.newHashSet(allScopes.keySet());
          toolsToCheck.removeAll(currentSeverityAndOccurrences.getOccurrences().keySet());
          boolean doContinue = false;
          final Map<String, HighlightSeverity> lowerScopeOccurrences = myScopeToAverageSeverityMap.get(myDefaultScopeName).getOccurrences();
          for (String toolName : toolsToCheck) {
            final HighlightSeverity currentToolSeverity = lowerScopeOccurrences.get(toolName);
            if (currentToolSeverity != null) {
              if (!currentSeverity.equals(currentToolSeverity)) {
                result.put(currentScope, ScopesAndSeveritiesTable.MIXED_FAKE_SEVERITY);
                doContinue = true;
                break;
              }
            }
          }
          if (doContinue) {
            continue;
          }
          result.put(currentScope, currentSeverity);
        }
      }

      return result;
    }

    public void put(@NotNull final ScopeToolState defaultState, @NotNull final List<ScopeToolState> nonDefault) {
      putOne(defaultState);
      if (myDefaultScopeName == null) {
        myDefaultScopeName = defaultState.getScopeName();
      }
      for (final ScopeToolState scopeToolState : nonDefault) {
        putOne(scopeToolState);
      }
    }

    private void putOne(final ScopeToolState state) {
      if (!state.isEnabled()) {
        return;
      }
      final Icon icon = state.getLevel().getIcon();
      final String scopeName = state.getScopeName();
      if (icon instanceof HighlightDisplayLevel.ColoredIcon) {
        final SeverityAndOccurrences severityAndOccurrences = myScopeToAverageSeverityMap.get(scopeName);
        final String inspectionName = state.getTool().getShortName();
        if (severityAndOccurrences == null) {
          myScopeToAverageSeverityMap.put(scopeName, new SeverityAndOccurrences().incOccurrences(inspectionName, state.getLevel().getSeverity()));
        } else {
          severityAndOccurrences.incOccurrences(inspectionName, state.getLevel().getSeverity());
        }
      }
    }
  }
}
