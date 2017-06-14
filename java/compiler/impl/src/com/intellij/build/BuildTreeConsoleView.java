/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.build;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.ProgressBuildEvent;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Vladislav.Soroka
 */
public class BuildTreeConsoleView implements ConsoleView, BuildConsoleView {

  @NonNls private static final String TREE = "tree";
  private final JPanel myPanel = new JPanel();
  private final CardLayout myCardLayout;
  private final JPanel myContentPanel = new JPanel();

  private final TreeTable myTreeTable;
  private final SimpleTreeBuilder myBuilder;
  private final Map<Object, ExecutionNode> nodesMap = ContainerUtil.newConcurrentMap();
  private final ExecutionNodeProgressAnimator myProgressAnimator;

  private final Project myProject;
  private final SimpleTreeStructure myTreeStructure;

  public BuildTreeConsoleView(Project project) {
    myProject = project;
    final ColumnInfo[] COLUMNS = {
      new TreeColumnInfo("name"),
      new ColumnInfo("time elapsed") {
        @Nullable
        @Override
        public Object valueOf(Object o) {
          if (o instanceof DefaultMutableTreeNode) {
            final Object userObject = ((DefaultMutableTreeNode)o).getUserObject();
            if (userObject instanceof ExecutionNode) {
              return ((ExecutionNode)userObject).getDuration() + "  ";
            }
          }
          return null;
        }
      }
    };
    final ExecutionNode rootNode = new ExecutionNode(myProject);
    final ListTreeTableModelOnColumns model = new ListTreeTableModelOnColumns(new DefaultMutableTreeNode(rootNode), COLUMNS);

    DefaultTableCellRenderer timeColumnCellRenderer = new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setHorizontalAlignment(SwingConstants.RIGHT);
        final Color fg = isSelected ? UIUtil.getTreeSelectionForeground() : SimpleTextAttributes.GRAY_ATTRIBUTES.getFgColor();
        setForeground(fg);
        return this;
      }
    };

    myTreeTable = new TreeTable(model) {
      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        if (column == 1) {
          return timeColumnCellRenderer;
        }
        return super.getCellRenderer(row, column);
      }
    };
    myTreeTable.setTableHeader(null);

    final TableColumn timeColumn = myTreeTable.getColumnModel().getColumn(1);
    final long duration = TimeUnit.HOURS.toMillis(10) + TimeUnit.MINUTES.toMillis(10) + 11111L;
    int timeColumnWidth = new JLabel("Running for " + StringUtil.formatDuration(duration), SwingConstants.RIGHT).getPreferredSize().width;
    timeColumn.setPreferredWidth(timeColumnWidth);
    timeColumn.setMinWidth(timeColumnWidth);
    timeColumn.setMaxWidth(timeColumnWidth);
    timeColumn.setResizable(false);

    TreeTableTree tree = myTreeTable.getTree();
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    myTreeStructure = new SimpleTreeStructure.Impl(rootNode);

    myBuilder = new SimpleTreeBuilder(tree, model, myTreeStructure, null);
    Disposer.register(this, myBuilder);
    myBuilder.initRootNode();
    myBuilder.updateFromRoot();
    myBuilder.expand(rootNode, null);

    myProgressAnimator = new ExecutionNodeProgressAnimator(myBuilder);
    myProgressAnimator.setCurrentNode(rootNode);

    myPanel.setLayout(new BorderLayout());
    myPanel.add(myContentPanel, BorderLayout.CENTER);
    myCardLayout = new CardLayout();
    myContentPanel.setLayout(myCardLayout);
    myContentPanel.add(ScrollPaneFactory.createScrollPane(myTreeTable), TREE);
  }

  private ExecutionNode getRootElement() {
    return ((ExecutionNode)myTreeStructure.getRootElement());
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
  }

  @Override
  public void clear() {
    getRootElement().removeChildren();
    nodesMap.clear();
    myBuilder.queueUpdate();
  }

  @Override
  public void scrollTo(int offset) {
  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
  }

  @Override
  public void setOutputPaused(boolean value) {
  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public boolean hasDeferredOutput() {
    return false;
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {
  }

  @Override
  public void setHelpId(@NotNull String helpId) {
  }

  @Override
  public void addMessageFilter(@NotNull Filter filter) {
  }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info) {
  }

  @Override
  public int getContentSize() {
    return 0;
  }

  @Override
  public boolean canPause() {
    return false;
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    return AnAction.EMPTY_ARRAY;
  }

  @Override
  public void allowHeavyFilters() {
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return null;
  }

  @Override
  public void dispose() {
  }

  @Override
  public String getViewId() {
    return "TREE";
  }

  @Override
  public void onEvent(BuildEvent event) {
    ExecutionNode parentNode = event.getParentId() == null ? getRootElement() : nodesMap.get(event.getParentId());
    if (parentNode == null) {
      parentNode = getRootElement();
    }
    ExecutionNode currentNode = nodesMap.get(event.getId());
    if (event instanceof StartBuildEvent) {
      assert currentNode == null;
      currentNode = new ExecutionNode(myProject);
      nodesMap.put(event.getId(), currentNode);
      parentNode.add(currentNode);
    }
    else {
      currentNode = nodesMap.get(event.getId());
      if (currentNode == null && event instanceof ProgressBuildEvent) {
        currentNode = new ExecutionNode(myProject);
        nodesMap.put(event.getId(), currentNode);
        parentNode.add(currentNode);
      }
    }

    if (currentNode == null) {
      // TODO log error
      return;
    }

    String eventMessage = event.getMessage();
    currentNode.setName(eventMessage);
    if (currentNode.getStartTime() == 0) {
      currentNode.setStartTime(event.getEventTime());
    }
    if (event instanceof FinishBuildEvent) {
      currentNode.setEndTime(event.getEventTime());
      myProgressAnimator.stopMovie();
    }

    myProgressAnimator.setCurrentNode(currentNode);
    myBuilder.queueUpdateFrom(currentNode, false, false);
  }
}
