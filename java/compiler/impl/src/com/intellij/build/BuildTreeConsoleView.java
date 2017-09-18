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

import com.intellij.build.events.*;
import com.intellij.build.events.impl.FailureImpl;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 */
public class BuildTreeConsoleView implements ConsoleView, DataProvider, BuildConsoleView {
  private static final Logger LOG = Logger.getInstance(BuildTreeConsoleView.class);

  @NonNls private static final String TREE = "tree";
  private final JPanel myPanel = new JPanel();
  private final SimpleTreeBuilder myBuilder;
  private final Map<Object, ExecutionNode> nodesMap = ContainerUtil.newConcurrentMap();
  private final ExecutionNodeProgressAnimator myProgressAnimator;

  private final Project myProject;
  private final SimpleTreeStructure myTreeStructure;
  private final DetailsHandler myDetailsHandler;
  private final TableColumn myTimeColumn;
  private volatile int myTimeColumnWidth;

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
              String duration = ((ExecutionNode)userObject).getDuration();
              updateTimeColumnWidth("_" + duration, false);
              return duration;
            }
          }
          return null;
        }
      }
    };
    final ExecutionNode rootNode = new ExecutionNode(myProject);
    rootNode.setAutoExpandNode(true);
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

    TreeTable treeTable = new TreeTable(model) {
      @Override
      public TableCellRenderer getCellRenderer(int row, int column) {
        if (column == 1) {
          return timeColumnCellRenderer;
        }
        return super.getCellRenderer(row, column);
      }
    };
    treeTable.setTableHeader(null);

    myTimeColumn = treeTable.getColumnModel().getColumn(1);
    myTimeColumn.setResizable(false);
    updateTimeColumnWidth("Running for " + StringUtil.formatDuration(11111L), true);

    TreeTableTree tree = treeTable.getTree();
    myTreeStructure = new SimpleTreeStructure.Impl(rootNode);

    myBuilder = new SimpleTreeBuilder(tree, model, myTreeStructure, null);
    Disposer.register(this, myBuilder);
    myBuilder.initRootNode();
    myBuilder.updateFromRoot();
    myBuilder.expand(rootNode, null);

    myProgressAnimator = new ExecutionNodeProgressAnimator(myBuilder);
    myProgressAnimator.setCurrentNode(rootNode);

    JPanel myContentPanel = new JPanel();
    myContentPanel.setLayout(new CardLayout());
    myContentPanel.add(ScrollPaneFactory.createScrollPane(treeTable, SideBorder.LEFT), TREE);

    myPanel.setLayout(new BorderLayout());
    ThreeComponentsSplitter myThreeComponentsSplitter = new ThreeComponentsSplitter() {
      @Override
      public void doLayout() {
        super.doLayout();
        JComponent detailsComponent = myDetailsHandler.getComponent();
        if (detailsComponent != null && detailsComponent.isVisible()) {
          int firstSize = getFirstSize();
          int lastSize = getLastSize();
          if (firstSize == 0 && lastSize == 0) {
            int width = Math.round(getWidth() / 2f);
            setFirstSize(width);
          }
        }
      }
    };
    Disposer.register(this, myThreeComponentsSplitter);
    myThreeComponentsSplitter.setFirstComponent(myContentPanel);
    myDetailsHandler = new DetailsHandler(myProject, tree, myThreeComponentsSplitter);
    myThreeComponentsSplitter.setLastComponent(myDetailsHandler.getComponent());
    myPanel.add(myThreeComponentsSplitter, BorderLayout.CENTER);
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
    myDetailsHandler.clear();
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
    return myBuilder.getTree();
  }

  @Override
  public void dispose() {
  }

  @Override
  public void onEvent(BuildEvent event) {
    ExecutionNode parentNode = event.getParentId() == null ? null : nodesMap.get(event.getParentId());
    ExecutionNode currentNode = nodesMap.get(event.getId());
    if (event instanceof StartEvent) {
      ExecutionNode rootElement = getRootElement();
      if (currentNode == null) {
        currentNode = event instanceof StartBuildEvent ? rootElement : new ExecutionNode(myProject);
        currentNode.setAutoExpandNode(currentNode == rootElement || parentNode == rootElement);
        nodesMap.put(event.getId(), currentNode);
      }
      else {
        LOG.warn("start event id collision found");
        return;
      }
      if (parentNode != null) {
        parentNode.add(currentNode);
      }

      if (event instanceof StartBuildEvent) {
        String buildTitle = ((StartBuildEvent)event).getBuildTitle();
        currentNode.setTitle(buildTitle);
        currentNode.setAutoExpandNode(true);
      }
    }
    else {
      currentNode = nodesMap.get(event.getId());
      if (currentNode == null && event instanceof ProgressBuildEvent) {
        currentNode = new ExecutionNode(myProject);
        nodesMap.put(event.getId(), currentNode);
        if (parentNode != null) {
          parentNode.add(currentNode);
        }
      }
    }

    if (currentNode == null) {
      // TODO log error
      return;
    }

    currentNode.setName(event.getMessage());
    currentNode.setHint(event.getHint());
    if (currentNode.getStartTime() == 0) {
      currentNode.setStartTime(event.getEventTime());
    }

    if (event instanceof FinishEvent) {
      currentNode.setEndTime(event.getEventTime());
      currentNode.setResult(((FinishEvent)event).getResult());
      int timeColumnWidth = new JLabel("__" + currentNode.getDuration(), SwingConstants.RIGHT).getPreferredSize().width;
      if (myTimeColumnWidth < timeColumnWidth) {
        myTimeColumnWidth = timeColumnWidth;
      }
    }

    myProgressAnimator.setCurrentNode(currentNode);
    myBuilder.queueUpdateFrom(currentNode, false, false);

    if (event instanceof FinishBuildEvent) {
      String aHint = event.getHint();
      String time = DateFormatUtil.formatDateTime(event.getEventTime());
      aHint = aHint == null ? "  at " + time : aHint + "  at " + time;
      currentNode.setHint(aHint);
      myProgressAnimator.stopMovie();
      updateTimeColumnWidth(myTimeColumnWidth);
      if (myDetailsHandler.myExecutionNode == null) {
        myDetailsHandler.setNode(getRootElement());
      }

      if (((FinishBuildEvent)event).getResult() instanceof FailureResult) {
        JTree tree = myBuilder.getTree();
        if (tree != null && !tree.isRootVisible()) {
          ExecutionNode rootElement = getRootElement();
          ExecutionNode resultNode = new ExecutionNode(myProject);
          resultNode.setName(StringUtil.toTitleCase(rootElement.getName()));
          resultNode.setHint(rootElement.getHint());
          resultNode.setEndTime(rootElement.getEndTime());
          resultNode.setStartTime(rootElement.getStartTime());
          resultNode.setResult(rootElement.getResult());
          resultNode.setTooltip(rootElement.getTooltip());
          rootElement.add(resultNode);
          myBuilder.queueUpdateFrom(resultNode, false, false);
        }
      }
    }
  }

  public void hideRootNode() {
    UIUtil.invokeLaterIfNeeded(() -> {
      JTree tree = myBuilder.getTree();
      if (tree != null) {
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
      }
    });
  }

  private void updateTimeColumnWidth(String text, boolean force) {
    int timeColumnWidth = new JLabel(text, SwingConstants.RIGHT).getPreferredSize().width;
    if (force || myTimeColumn.getMaxWidth() < timeColumnWidth) {
      updateTimeColumnWidth(timeColumnWidth);
    }
  }

  private void updateTimeColumnWidth(int width) {
    myTimeColumn.setPreferredWidth(width);
    myTimeColumn.setMinWidth(width);
    myTimeColumn.setMaxWidth(width);
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) return "reference.build.tool.window";
    if (CommonDataKeys.PROJECT.is(dataId)) return myProject;
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) return extractNavigatables();
    return null;
  }

  private Object extractNavigatables() {
    final List<Navigatable> navigatables = new ArrayList<>();
    for (ExecutionNode each : getSelectedNodes()) {
      List<Navigatable> navigatable = each.getNavigatables();
      navigatables.addAll(navigatable);
    }
    return navigatables.isEmpty() ? null : navigatables.toArray(new Navigatable[navigatables.size()]);
  }

  private ExecutionNode[] getSelectedNodes() {
    JTree tree = myBuilder.getTree();
    if (tree instanceof Tree) {
      DefaultMutableTreeNode[] selectedNodes = ((Tree)tree).getSelectedNodes(DefaultMutableTreeNode.class, null);
      return Arrays.stream(selectedNodes)
        .map(DefaultMutableTreeNode::getUserObject)
        .filter(userObject -> userObject instanceof ExecutionNode)
        .map(ExecutionNode.class::cast)
        .distinct().toArray(ExecutionNode[]::new);
    }
    return new ExecutionNode[0];
  }

  private static class DetailsHandler {
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern A_PATTERN = Pattern.compile("<a ([^>]* )?href=[\"\']([^>]*)[\"\'][^>]*>");
    private static final String A_CLOSING = "</a>";
    private static final Set<String> NEW_LINES = ContainerUtil.set("<br>", "</br>", "<br/>", "<p>", "</p>", "<p/>", "<pre>", "</pre>");

    private final ThreeComponentsSplitter mySplitter;
    @Nullable
    private ExecutionNode myExecutionNode;
    private final ConsoleView myConsole;
    private final JPanel myPanel;

    public DetailsHandler(Project project,
                          TreeTableTree tree,
                          ThreeComponentsSplitter threeComponentsSplitter) {
      myConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
      mySplitter = threeComponentsSplitter;
      myPanel = new JPanel(new BorderLayout());
      JComponent consoleComponent = myConsole.getComponent();
      AnAction[] consoleActions = myConsole.createConsoleActions();
      consoleComponent.setFocusable(true);
      final Color editorBackground = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
      consoleComponent.setBorder(new CompoundBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT | SideBorder.TOP),
                                                    new SideBorder(editorBackground, SideBorder.LEFT)));
      myPanel.add(consoleComponent, BorderLayout.CENTER);
      final ActionToolbar toolbar = ActionManager.getInstance()
        .createActionToolbar("BuildResults", new DefaultActionGroup(consoleActions), false);
      myPanel.add(toolbar.getComponent(), BorderLayout.EAST);
      myPanel.setVisible(false);
      tree.addTreeSelectionListener(new TreeSelectionListener() {
        @Override
        public void valueChanged(TreeSelectionEvent e) {
          TreePath path = tree.getSelectionPath();
          setNode(path != null ? (DefaultMutableTreeNode)path.getLastPathComponent() : null);
        }
      });

      Disposer.register(threeComponentsSplitter, myConsole);
    }

    public boolean setNode(@NotNull ExecutionNode node) {
      EventResult eventResult = node.getResult();
      if (!(eventResult instanceof FailureResult)) return false;
      List<? extends Failure> failures = ((FailureResult)eventResult).getFailures();
      if (failures.isEmpty()) return false;
      myConsole.clear();

      boolean hasChanged = false;
      for (Iterator<? extends Failure> iterator = failures.iterator(); iterator.hasNext(); ) {
        Failure failure = iterator.next();
        String text = ObjectUtils.chooseNotNull(failure.getDescription(), failure.getMessage());
        if (text == null && failure.getError() != null) {
          text = failure.getError().getMessage();
        }
        if (text == null) continue;
        printDetails((FailureImpl)failure, text);
        hasChanged = true;
        if (iterator.hasNext()) {
          myConsole.print("\n\n", ConsoleViewContentType.NORMAL_OUTPUT);
        }
      }
      if (!hasChanged) return false;

      myConsole.scrollTo(0);
      int firstSize = mySplitter.getFirstSize();
      int lastSize = mySplitter.getLastSize();

      if (firstSize == 0 && lastSize == 0) {
        int width = Math.round(mySplitter.getWidth() / 2f);
        mySplitter.setFirstSize(width);
      }
      myPanel.setVisible(true);
      return true;
    }

    public void printDetails(FailureImpl failure, String text) {
      String content = StringUtil.convertLineSeparators(text);
      while (true) {
        Matcher tagMatcher = TAG_PATTERN.matcher(content);
        if (!tagMatcher.find()) {
          myConsole.print(content, ConsoleViewContentType.ERROR_OUTPUT);
          break;
        }
        String tagStart = tagMatcher.group();
        myConsole.print(content.substring(0, tagMatcher.start()), ConsoleViewContentType.ERROR_OUTPUT);
        Matcher aMatcher = A_PATTERN.matcher(tagStart);
        if (aMatcher.matches()) {
          final String href = aMatcher.group(2);
          int linkEnd = content.indexOf(A_CLOSING, tagMatcher.end());
          if (linkEnd > 0) {
            String linkText = content.substring(tagMatcher.end(), linkEnd).replaceAll(TAG_PATTERN.pattern(), "");
            myConsole.printHyperlink(linkText, new HyperlinkInfo() {
              @Override
              public void navigate(Project project) {
                NotificationData notificationData = failure.getNotificationData();
                if (notificationData != null) {
                  notificationData.getListener().hyperlinkUpdate(
                    notificationData.getNotification(),
                    IJSwingUtilities.createHyperlinkEvent(href, myConsole.getComponent()));
                }
              }
            });
            content = content.substring(linkEnd + A_CLOSING.length());
            continue;
          }
        }
        if (NEW_LINES.contains(tagStart)) {
          myConsole.print("\n", ConsoleViewContentType.SYSTEM_OUTPUT);
        }
        else {
          myConsole.print(content.substring(tagMatcher.start(), tagMatcher.end()), ConsoleViewContentType.ERROR_OUTPUT);
        }
        content = content.substring(tagMatcher.end());
      }
    }

    public void setNode(@Nullable DefaultMutableTreeNode node) {
      if (node == null || node.getUserObject() == myExecutionNode) return;
      if (node.getUserObject() instanceof ExecutionNode) {
        myExecutionNode = (ExecutionNode)node.getUserObject();
        if (setNode((ExecutionNode)node.getUserObject())) {
          return;
        }
      }

      myExecutionNode = null;
      myPanel.setVisible(false);
    }

    public JComponent getComponent() {
      return myPanel;
    }

    public void clear() {
      myPanel.setVisible(false);
      myConsole.clear();
    }
  }
}
