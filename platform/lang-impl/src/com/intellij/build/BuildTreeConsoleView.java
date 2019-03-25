// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build;

import com.intellij.build.events.*;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.*;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.tree.treeTable.TreeTableModelWithColumns;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.intellij.build.BuildView.CONSOLE_VIEW_NAME;

/**
 * @author Vladislav.Soroka
 */
public class BuildTreeConsoleView implements ConsoleView, DataProvider, BuildConsoleView, Filterable<ExecutionNode> {
  private static final Logger LOG = Logger.getInstance(BuildTreeConsoleView.class);

  @NonNls private static final String TREE = "tree";
  @NonNls private static final String SPLITTER_PROPERTY = "SMTestRunner.Splitter.Proportion";
  private final JPanel myPanel = new JPanel();
  private final Map<Object, ExecutionNode> nodesMap = ContainerUtil.newConcurrentMap();

  private final Project myProject;
  private final ConsoleViewHandler myConsoleViewHandler;
  @NotNull
  private final BuildViewSettingsProvider myViewSettingsProvider;
  private final TableColumn myTimeColumn;
  private final String myWorkingDir;
  private final AtomicBoolean myDisposed = new AtomicBoolean();
  private final StructureTreeModel<SimpleTreeStructure> myTreeModel;
  private final TreeTableTree myTree;
  private final ExecutionNode myRootNode;
  private final ExecutionNode myBuildProgressRootNode;
  @NotNull private final BuildViewGroupingSupport myGroupingSupport;
  private final ConcurrentLinkedDeque<Pair<BuildEvent, ExecutionNode>> myGroupingEvents = new ConcurrentLinkedDeque<>();
  private volatile int myTimeColumnWidth;
  @Nullable
  private volatile Predicate<ExecutionNode> myExecutionTreeFilter;

  public BuildTreeConsoleView(Project project,
                              BuildDescriptor buildDescriptor,
                              @Nullable ExecutionConsole executionConsole,
                              @NotNull BuildViewSettingsProvider buildViewSettingsProvider) {
    myProject = project;
    myWorkingDir = FileUtil.toSystemIndependentName(buildDescriptor.getWorkingDir());
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
              updateTimeColumnWidth("___" + duration, false);
              return duration;
            }
          }
          return null;
        }
      }
    };
    myViewSettingsProvider = buildViewSettingsProvider;
    myGroupingSupport = new BuildViewGroupingSupport(this);
    myGroupingSupport.addPropertyChangeListener(e -> changeGrouping());

    myRootNode = new ExecutionNode(myProject, null);
    myRootNode.setAutoExpandNode(true);
    myBuildProgressRootNode = new ExecutionNode(myProject, myRootNode);
    myRootNode.add(myBuildProgressRootNode);

    SimpleTreeStructure treeStructure = new SimpleTreeStructure.Impl(myRootNode);
    myTreeModel = new StructureTreeModel<>(treeStructure);
    final TreeTableModel model = new TreeTableModelWithColumns(new AsyncTreeModel(myTreeModel, this), COLUMNS);

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
        Color fg = isSelected ? UIUtil.getTreeSelectionForeground(hasFocus) : SimpleTextAttributes.GRAY_ATTRIBUTES.getFgColor();
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
    treeTable.setRootVisible(false);
    EditSourceOnDoubleClickHandler.install(treeTable);
    EditSourceOnEnterKeyHandler.install(treeTable, null);

    myTree = treeTable.getTree();
    treeTable.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        treeTable.setSelectionBackground(UIUtil.getTreeSelectionBackground(true));
      }

      @Override
      public void focusLost(FocusEvent e) {
        treeTable.setSelectionBackground(UIUtil.getTreeSelectionBackground(false));
      }
    });
    final TreeCellRenderer treeCellRenderer = myTree.getCellRenderer();
    myTree.setCellRenderer((tree, value, selected, expanded, leaf, row, hasFocus) -> {
      final Component rendererComponent =
        treeCellRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      if (rendererComponent instanceof SimpleColoredComponent) {
        Color bg = UIUtil.getTreeBackground(selected, true);
        Color fg = UIUtil.getTreeForeground(selected, true);
        if (selected) {
          for (SimpleColoredComponent.ColoredIterator it = ((SimpleColoredComponent)rendererComponent).iterator(); it.hasNext(); ) {
            it.next();
            int offset = it.getOffset();
            int endOffset = it.getEndOffset();
            SimpleTextAttributes currentAttributes = it.getTextAttributes();
            SimpleTextAttributes newAttributes =
              new SimpleTextAttributes(bg, fg, currentAttributes.getWaveColor(), currentAttributes.getStyle());
            it.split(endOffset - offset, newAttributes);
          }
        }

        SpeedSearchUtil.applySpeedSearchHighlighting(treeTable, (SimpleColoredComponent)rendererComponent, true, selected);
      }
      return rendererComponent;
    });
    new TreeTableSpeedSearch(treeTable).setComparator(new SpeedSearchComparator(false));
    treeTable.setTableHeader(null);

    myTimeColumn = treeTable.getColumnModel().getColumn(1);
    myTimeColumn.setResizable(false);
    updateTimeColumnWidth(StringUtil.formatDurationApproximate(11111L), true);

    TreeUtil.installActions(myTree);

    JPanel myContentPanel = new JPanel();
    myContentPanel.setLayout(new CardLayout());
    myContentPanel.add(ScrollPaneFactory.createScrollPane(treeTable, SideBorder.LEFT), TREE);

    myPanel.setLayout(new BorderLayout());
    ThreeComponentsSplitter myThreeComponentsSplitter = new ThreeComponentsSplitter() {
      @Override
      public void setFirstSize(int size) {
        super.setFirstSize(size);
        float proportion = size / (float)getWidth();
        PropertiesComponent.getInstance().setValue(SPLITTER_PROPERTY, proportion, 0.3f);
      }

      @Override
      public void doLayout() {
        super.doLayout();
        JComponent detailsComponent = myConsoleViewHandler.getComponent();
        if (detailsComponent != null && detailsComponent.isVisible()) {
          updateSplitter(this);
        }
      }
    };
    Disposer.register(this, myThreeComponentsSplitter);
    myThreeComponentsSplitter.setFirstComponent(myContentPanel);
    myConsoleViewHandler =
      new ConsoleViewHandler(myProject, myTree, myBuildProgressRootNode, myThreeComponentsSplitter, executionConsole,
                             buildViewSettingsProvider);
    myThreeComponentsSplitter.setLastComponent(myConsoleViewHandler.getComponent());
    myPanel.add(myThreeComponentsSplitter, BorderLayout.CENTER);
  }

  @Override
  public void clear() {
    getRootElement().removeChildren();
    nodesMap.clear();
    myConsoleViewHandler.clear();
    myTreeModel.invalidate();
  }

  @Override
  public boolean isFilteringEnabled() {
    return true;
  }

  @Override
  @Nullable
  public Predicate<ExecutionNode> getFilter() {
    return myExecutionTreeFilter;
  }

  @Override
  public void setFilter(@Nullable Predicate<ExecutionNode> executionTreeFilter) {
    myExecutionTreeFilter = executionTreeFilter;
    ExecutionNode rootElement = getRootElement();
    rootElement.setFilter(executionTreeFilter);
    scheduleUpdate(rootElement);
  }

  private ExecutionNode getRootElement() {
    return myRootNode;
  }

  public ExecutionNode getBuildProgressRootNode() {
    return myBuildProgressRootNode;
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
  }

  @Nullable
  private ExecutionNode getOrMaybeCreateParentNode(@NotNull BuildEvent event) {
    ExecutionNode parentNode = event.getParentId() == null ? null : nodesMap.get(event.getParentId());
    if (event instanceof MessageEvent) {
      if (parentNode == getBuildProgressRootNode()) {
        parentNode = getRootElement();
      }
      parentNode = createMessageParentNodes((MessageEvent)event, parentNode);
    }
    return parentNode;
  }

  public void onEventInternal(@NotNull BuildEvent event) {
    final ExecutionNode parentNode = getOrMaybeCreateParentNode(event);
    final Object eventId = myGroupingSupport.getGroupedId(event);
    ExecutionNode currentNode = nodesMap.get(eventId);
    if (event instanceof StartEvent || event instanceof MessageEvent) {
      if (currentNode == null) {
        if (event instanceof StartBuildEvent) {
          currentNode = getBuildProgressRootNode();
          installContextMenu((StartBuildEvent)event);
          String buildTitle = ((StartBuildEvent)event).getBuildTitle();
          currentNode.setTitle(buildTitle);
        }
        else {
          currentNode = new ExecutionNode(myProject, parentNode);

          if (event instanceof MessageEvent) {
            MessageEvent messageEvent = (MessageEvent)event;
            currentNode.setStartTime(messageEvent.getEventTime());
            currentNode.setEndTime(messageEvent.getEventTime());
            currentNode.setNavigatable(messageEvent.getNavigatable(myProject));
            final MessageEventResult messageEventResult = messageEvent.getResult();
            currentNode.setResult(messageEventResult);
            myGroupingEvents.add(Pair.pair(event, currentNode));
          }
        }
        nodesMap.put(eventId, currentNode);
      }
      else {
        LOG.warn("start event id collision found:" + eventId + ", was also in node: " + currentNode.getTitle());
        return;
      }

      if (parentNode != null) {
        parentNode.add(currentNode);
      }
    }
    else {
      currentNode = nodesMap.get(eventId);
      if (currentNode == null && event instanceof ProgressBuildEvent) {
        currentNode = new ExecutionNode(myProject, parentNode);
        nodesMap.put(eventId, currentNode);
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
      final String text = "__" + currentNode.getDuration();
      ApplicationManager.getApplication().invokeLater(() -> {
        int timeColumnWidth = new JLabel(text, SwingConstants.RIGHT).getPreferredSize().width;
        if (myTimeColumnWidth < timeColumnWidth) {
          myTimeColumnWidth = timeColumnWidth;
        }
      });
    }

    if (event instanceof FinishBuildEvent) {
      String aHint = event.getHint();
      String time = DateFormatUtil.formatDateTime(event.getEventTime());
      aHint = aHint == null ? "at " + time : aHint + " at " + time;
      currentNode.setHint(aHint);
      updateTimeColumnWidth(myTimeColumnWidth);
      if (myConsoleViewHandler.myExecutionNode == null) {
        ExecutionNode element = getBuildProgressRootNode();
        ApplicationManager.getApplication().invokeLater(() -> myConsoleViewHandler.setNode(element));
      }
    }
    scheduleUpdate(currentNode);
  }

  private void installContextMenu(@NotNull StartBuildEvent startBuildEvent) {
    UIUtil.invokeLaterIfNeeded(() -> {
      final DefaultActionGroup group = new DefaultActionGroup();
      final DefaultActionGroup rerunActionGroup = new DefaultActionGroup();
      AnAction[] restartActions = startBuildEvent.getRestartActions();
      for (AnAction anAction : restartActions) {
        rerunActionGroup.add(anAction);
      }
      if (restartActions.length > 0) {
        group.addAll(rerunActionGroup);
        group.addSeparator();
      }
      EditSourceAction edit = new EditSourceAction();
      ActionUtil.copyFrom(edit, "EditSource");
      group.add(edit);
      group.addSeparator();
      group.add(new ShowExecutionErrorsOnlyAction(this));

      TreeTable treeTable = myTree.getTreeTable();
      PopupHandler.installPopupHandler(treeTable, group, "BuildView");
    });
  }

  @Override
  public void scrollTo(int offset) {
  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public void setOutputPaused(boolean value) {
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
    return myTree;
  }

  @Override
  public void dispose() {
    myDisposed.set(true);
  }

  public boolean isDisposed() {
    return myDisposed.get();
  }

  @Override
  public void onEvent(@NotNull BuildEvent event) {
    myTreeModel.getInvoker().runOrInvokeLater(() -> onEventInternal(event));
  }

  protected void expand(TreeTableTree tree) {
    TreeUtil.expand(tree,
                    path -> {
                      ExecutionNode node = TreeUtil.getLastUserObject(ExecutionNode.class, path);
                      if (node != null && node.isAutoExpandNode() && node.getChildCount() > 0) {
                        return TreeVisitor.Action.CONTINUE;
                      } else {
                        return TreeVisitor.Action.SKIP_CHILDREN;
                      }
                    },
                    path -> {});
  }

  void scheduleUpdate(ExecutionNode executionNode) {
    SimpleNode node = executionNode.getParent() == null ? executionNode : executionNode.getParent();
    myTreeModel.invalidate(node, true).onProcessed(p -> expand(myTree));
  }

  private ExecutionNode createMessageParentNodes(MessageEvent messageEvent, ExecutionNode parentNode) {
    Object messageEventParentId = messageEvent.getParentId();
    if (messageEventParentId == null) return null;

    String group = messageEvent.getGroup();
    String groupNodeId = group.hashCode() + messageEventParentId.toString();
    ExecutionNode messagesGroupNode =
      getOrCreateMessagesNode(messageEvent, groupNodeId, parentNode, null, group, true, null, null, nodesMap, myProject);

    EventResult groupNodeResult = messagesGroupNode.getResult();
    final MessageEvent.Kind eventKind = messageEvent.getKind();
    if (!(groupNodeResult instanceof MessageEventResult) ||
        ((MessageEventResult)groupNodeResult).getKind().compareTo(eventKind) > 0) {
      messagesGroupNode.setResult((MessageEventResult)() -> eventKind);
    }
    if (messageEvent instanceof FileMessageEvent) {
      ExecutionNode fileParentNode = messagesGroupNode;
      FilePosition filePosition = ((FileMessageEvent)messageEvent).getFilePosition();
      String filePath = FileUtil.toSystemIndependentName(filePosition.getFile().getPath());
      String parentsPath = "";

      String relativePath = FileUtil.getRelativePath(myWorkingDir, filePath, '/');
      if (relativePath != null) {
        parentsPath = myWorkingDir;
      }

      boolean groupBySourceRoot = myGroupingSupport.get(BuildViewGroupingSupport.SOURCE_ROOT_GROUPING);
      if (groupBySourceRoot && relativePath != null) {
        String nodeId = groupNodeId + myWorkingDir;
        ExecutionNode workingDirNode = getOrCreateMessagesNode(messageEvent, nodeId, messagesGroupNode, myWorkingDir, null, false,
                                                               () -> AllIcons.Nodes.Module, null, nodesMap, myProject);
        parentsPath = myWorkingDir;
        fileParentNode = workingDirNode;
      }

      VirtualFile sourceRootForFile;
      VirtualFile ioFile = VfsUtil.findFileByIoFile(new File(filePath), false);
      if (groupBySourceRoot && ioFile != null &&
          (sourceRootForFile = ProjectFileIndex.SERVICE.getInstance(myProject).getSourceRootForFile(ioFile)) != null) {
        relativePath = FileUtil.getRelativePath(parentsPath, sourceRootForFile.getPath(), '/');
        if (relativePath != null) {
          parentsPath += ("/" + relativePath);
          String contentRootNodeId = groupNodeId + sourceRootForFile.getPath();
          fileParentNode = getOrCreateMessagesNode(messageEvent, contentRootNodeId, fileParentNode, relativePath, null, false,
                                                   () -> ProjectFileIndex.SERVICE.getInstance(myProject).isInTestSourceContent(ioFile)
                                                         ? AllIcons.Modules.TestRoot
                                                         : AllIcons.Modules.SourceRoot, null, nodesMap, myProject);
        }
      }

      String fileNodeId = groupNodeId + filePath + groupBySourceRoot;
      relativePath = StringUtil.isEmpty(parentsPath) ? filePath : FileUtil.getRelativePath(parentsPath, filePath, '/');
      parentNode = getOrCreateMessagesNode(messageEvent, fileNodeId, fileParentNode, relativePath, null, true,
                                           () -> {
                                             VirtualFile file = VfsUtil.findFileByIoFile(filePosition.getFile(), false);
                                             if (file != null) {
                                               return file.getFileType().getIcon();
                                             }
                                             return null;
                                           }, messageEvent.getNavigatable(myProject), nodesMap, myProject);
    }
    else {
      parentNode = messagesGroupNode;
    }

    if (eventKind == MessageEvent.Kind.ERROR || eventKind == MessageEvent.Kind.WARNING) {
      SimpleNode p = parentNode;
      do {
        ((ExecutionNode)p).reportChildMessageKind(eventKind);
      }
      while ((p = p.getParent()) instanceof ExecutionNode);
      scheduleUpdate(getRootElement());
    }
    return parentNode;
  }

  public void hideRootNode() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myTree != null) {
        myTree.setRootVisible(false);
        myTree.setShowsRootHandles(true);
      }
    });
  }

  private void updateTimeColumnWidth(String text, boolean force) {
    int timeColumnWidth = new JLabel(text, SwingConstants.RIGHT).getPreferredSize().width;
    if (myTimeColumnWidth > timeColumnWidth) {
      timeColumnWidth = myTimeColumnWidth;
    }

    if (force || myTimeColumn.getMaxWidth() < timeColumnWidth || myTimeColumn.getWidth() < timeColumnWidth) {
      updateTimeColumnWidth(timeColumnWidth);
    }
  }

  private void changeGrouping() {
    for (Pair<BuildEvent, ExecutionNode> pair : myGroupingEvents) {
      BuildEvent event = pair.first;
      ExecutionNode originalNode = pair.second;
      Object id = myGroupingSupport.getGroupedId(event);
      ExecutionNode node = nodesMap.get(id);
      if (node == null && event instanceof MessageEvent) {
        ExecutionNode parentNode = getOrMaybeCreateParentNode(event);
        if (parentNode != null) {
          node = originalNode.copy(parentNode);
          nodesMap.put(id, node);
          parentNode.add(node);
        }
      }

      Collection<Object> allGroupedIds = myGroupingSupport.getAllGroupedIds(event);
      for (Object _id : allGroupedIds) {
        if (_id == id) continue;
        ExecutionNode _node = nodesMap.get(_id);
        _node.setVisible(false);
        ExecutionNode p = (ExecutionNode)_node.getParent();
        while (p.getParent() instanceof ExecutionNode) {
          p.setVisible(false);
          p = (ExecutionNode)p.getParent();
        }
        scheduleUpdate(p);
      }

      if (node != null) {
        node.setVisible(true);
        ExecutionNode p = (ExecutionNode)node.getParent();
        while (p.getParent() instanceof ExecutionNode) {
          p.setVisible(true);
          scheduleUpdate(p);
          p = (ExecutionNode)p.getParent();
        }
        scheduleUpdate(node);
      }
    }
  }

  private void updateTimeColumnWidth(int width) {
    myTimeColumn.setPreferredWidth(width);
    myTimeColumn.setMinWidth(width);
    myTimeColumn.setMaxWidth(width);
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) return "reference.build.tool.window";
    if (CommonDataKeys.PROJECT.is(dataId)) return myProject;
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) return extractNavigatables();
    if (BuildViewGroupingSupport.KEY.is(dataId)) return myGroupingSupport;
    return null;
  }

  private Object extractNavigatables() {
    final List<Navigatable> navigatables = new ArrayList<>();
    for (ExecutionNode each : getSelectedNodes()) {
      List<Navigatable> navigatable = each.getNavigatables();
      navigatables.addAll(navigatable);
    }
    return navigatables.isEmpty() ? null : navigatables.toArray(new Navigatable[0]);
  }

  private ExecutionNode[] getSelectedNodes() {
    final ExecutionNode[] result = new ExecutionNode[0];
    if (myTree != null) {
      final List<ExecutionNode> nodes =
        TreeUtil.collectSelectedObjects(myTree, path -> TreeUtil.getLastUserObject(ExecutionNode.class, path));
      return nodes.toArray(result);
    }
    return result;
  }

  @TestOnly
  JTree getTree() {
    return myTree;
  }

  private static void updateSplitter(@NotNull ThreeComponentsSplitter myThreeComponentsSplitter) {
    int firstSize = myThreeComponentsSplitter.getFirstSize();
    int splitterWidth = myThreeComponentsSplitter.getWidth();
    if (firstSize == 0) {
      float proportion = PropertiesComponent.getInstance().getFloat(SPLITTER_PROPERTY, 0.3f);
      int width = Math.round(splitterWidth * proportion);
      if (width > 0) {
        myThreeComponentsSplitter.setFirstSize(width);
      }
    }
  }

  @NotNull
  private static ExecutionNode getOrCreateMessagesNode(MessageEvent messageEvent,
                                                       String nodeId,
                                                       ExecutionNode parentNode,
                                                       String nodeName,
                                                       String nodeTitle,
                                                       boolean autoExpandNode,
                                                       @Nullable Supplier<? extends Icon> iconProvider,
                                                       @Nullable Navigatable navigatable,
                                                       Map<Object, ExecutionNode> nodesMap,
                                                       Project project) {
    ExecutionNode node = nodesMap.get(nodeId);
    if (node == null) {
      node = new ExecutionNode(project, parentNode);
      node.setName(nodeName);
      node.setTitle(nodeTitle);
      if (autoExpandNode) {
        node.setAutoExpandNode(true);
      }
      node.setStartTime(messageEvent.getEventTime());
      node.setEndTime(messageEvent.getEventTime());
      if (iconProvider != null) {
        node.setIconProvider(iconProvider);
      }
      if (navigatable != null) {
        node.setNavigatable(navigatable);
      }
      parentNode.add(node);
      nodesMap.put(nodeId, node);
    }
    return node;
  }

  private static class ConsoleViewHandler {
    private static final String TASK_OUTPUT_VIEW_NAME = "taskOutputView";
    private final JPanel myPanel;
    private final CompositeView<ExecutionConsole> myView;
    @NotNull
    private final BuildViewSettingsProvider myViewSettingsProvider;
    @NotNull
    private final ExecutionNode myBuildProgressRootNode;
    @Nullable
    private ExecutionNode myExecutionNode;

    ConsoleViewHandler(Project project,
                       TreeTableTree tree,
                       @NotNull ExecutionNode buildProgressRootNode,
                       ThreeComponentsSplitter threeComponentsSplitter,
                       @Nullable ExecutionConsole executionConsole,
                       @NotNull BuildViewSettingsProvider buildViewSettingsProvider) {
      myBuildProgressRootNode = buildProgressRootNode;
      myPanel = new JPanel(new BorderLayout());
      ConsoleView myNodeConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
      myViewSettingsProvider = buildViewSettingsProvider;
      myView = new CompositeView<>(null);
      if (executionConsole != null && buildViewSettingsProvider.isSideBySideView()) {
        myView.addView(executionConsole, CONSOLE_VIEW_NAME, true);
      }
      myView.addView(myNodeConsole, TASK_OUTPUT_VIEW_NAME, false);
      if (buildViewSettingsProvider.isSideBySideView()) {
        myView.enableView(CONSOLE_VIEW_NAME, false);
        myPanel.setVisible(true);
      }
      else {
        myPanel.setVisible(false);
      }
      JComponent consoleComponent = myNodeConsole.getComponent();
      AnAction[] consoleActions = myNodeConsole.createConsoleActions();
      consoleComponent.setFocusable(true);
      final Color editorBackground = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
      consoleComponent.setBorder(new CompoundBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT),
                                                    new SideBorder(editorBackground, SideBorder.LEFT)));
      myPanel.add(myView.getComponent(), BorderLayout.CENTER);
      final ActionToolbar toolbar = ActionManager.getInstance()
        .createActionToolbar("BuildResults", new DefaultActionGroup(consoleActions), false);
      myPanel.add(toolbar.getComponent(), BorderLayout.EAST);
      tree.addTreeSelectionListener(e -> {
        TreePath path = e.getPath();
        if (path == null || !e.isAddedPath()) {
          return;
        }
        TreePath selectionPath = tree.getSelectionPath();
        setNode(selectionPath != null ? (DefaultMutableTreeNode)selectionPath.getLastPathComponent() : null);
      });

      Disposer.register(threeComponentsSplitter, myView);
      Disposer.register(threeComponentsSplitter, myNodeConsole);
    }

    private ConsoleView getTaskOutputView() {
      return (ConsoleView)myView.getView(TASK_OUTPUT_VIEW_NAME);
    }

    public boolean setNode(@NotNull ExecutionNode node) {
      if (node == myBuildProgressRootNode) return false;
      EventResult eventResult = node.getResult();
      boolean hasChanged = false;

      ConsoleView taskOutputView = getTaskOutputView();
      if (eventResult instanceof FailureResult) {
        taskOutputView.clear();
        List<? extends Failure> failures = ((FailureResult)eventResult).getFailures();
        if (failures.isEmpty()) return false;
        for (Iterator<? extends Failure> iterator = failures.iterator(); iterator.hasNext(); ) {
          Failure failure = iterator.next();
          String text = ObjectUtils.chooseNotNull(failure.getDescription(), failure.getMessage());
          if (text == null && failure.getError() != null) {
            text = failure.getError().getMessage();
          }
          if (text == null) continue;
          printDetails(failure, text);
          hasChanged = true;
          if (iterator.hasNext()) {
            taskOutputView.print("\n\n", ConsoleViewContentType.NORMAL_OUTPUT);
          }
        }
      }
      else if (eventResult instanceof MessageEventResult) {
        String details = ((MessageEventResult)eventResult).getDetails();
        if (details == null) {
          return false;
        }
        if (details.isEmpty()) {
          return false;
        }
        taskOutputView.clear();
        printDetails(null, details);
        hasChanged = true;
      }

      if (!hasChanged) return false;

      taskOutputView.scrollTo(0);

      myView.enableView(TASK_OUTPUT_VIEW_NAME, !myViewSettingsProvider.isSideBySideView());
      myPanel.setVisible(true);
      return true;
    }

    private void printDetails(Failure failure, @Nullable String details) {
      BuildConsoleUtils.printDetails(getTaskOutputView(), failure, details);
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
      if (myView.getView(CONSOLE_VIEW_NAME) != null && myViewSettingsProvider.isSideBySideView()) {
        myView.enableView(CONSOLE_VIEW_NAME, false);
        myPanel.setVisible(true);
      }
      else {
        myPanel.setVisible(false);
      }
    }

    public JComponent getComponent() {
      return myPanel;
    }

    public void clear() {
      myPanel.setVisible(false);
      getTaskOutputView().clear();
    }
  }
}
