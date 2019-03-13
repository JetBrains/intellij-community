// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build;

import com.intellij.build.events.*;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
public class BuildTreeConsoleView implements ConsoleView, DataProvider, BuildConsoleView {
  private static final Logger LOG = Logger.getInstance(BuildTreeConsoleView.class);

  @NonNls private static final String TREE = "tree";
  private final JPanel myPanel = new JPanel();
  private final Map<Object, ExecutionNode> nodesMap = ContainerUtil.newConcurrentMap();

  private final Project myProject;
  private final DetailsHandler myDetailsHandler;
  private final TableColumn myTimeColumn;
  private final String myWorkingDir;
  private final AtomicBoolean myDisposed = new AtomicBoolean();
  private final StructureTreeModel<SimpleTreeStructure> myTreeModel;
  private final TreeTableTree myTree;
  private final ExecutionNode myRootNode;
  private volatile int myTimeColumnWidth;
  private int myBuildStepNodePosition = 0;
  private ExecutionNode myBuildStepsNode;

  public BuildTreeConsoleView(Project project, BuildDescriptor buildDescriptor) {
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
    myRootNode = new ExecutionNode(myProject, null);
    myRootNode.setAutoExpandNode(true);
    myBuildStepsNode = new ExecutionNode(myProject, myRootNode);
    myRootNode.add(myBuildStepsNode);

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
    updateTimeColumnWidth("Running for " + StringUtil.formatDuration(11111L), true);

    TreeUtil.installActions(myTree);

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
            if (width > 0) {
              setFirstSize(width);
            }
          }
        }
      }
    };
    Disposer.register(this, myThreeComponentsSplitter);
    myThreeComponentsSplitter.setFirstComponent(myContentPanel);
    myDetailsHandler = new DetailsHandler(myProject, myTree, myThreeComponentsSplitter);
    myThreeComponentsSplitter.setLastComponent(myDetailsHandler.getComponent());
    myPanel.add(myThreeComponentsSplitter, BorderLayout.CENTER);
    hideRootNode();
  }

  private ExecutionNode getRootElement() {
    return myRootNode;
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
  }

  @Override
  public void clear() {
    getRootElement().removeChildren();
    getRootElement().add(myBuildStepsNode);
    nodesMap.clear();
    myDetailsHandler.clear();
    myTreeModel.invalidate();
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

  public void onEventInternal(@NotNull BuildEvent event) {
    ExecutionNode parentNode = event.getParentId() == null ? null : nodesMap.get(event.getParentId());
    ExecutionNode currentNode = nodesMap.get(event.getId());
    if (event instanceof StartEvent || event instanceof MessageEvent) {
      ExecutionNode rootElement = myBuildStepsNode;
      if (currentNode != null) {
        LOG.warn("start event id collision found:" + event.getId() + ", was also in node: " + currentNode.getTitle());
        return;
      }

      if (event instanceof StartBuildEvent) {
        currentNode = rootElement;
      }
      else {
        if (event instanceof MessageEvent) {
          MessageEvent messageEvent = (MessageEvent)event;
          parentNode = createMessageParentNodes(messageEvent, parentNode);
        }
        currentNode = new ExecutionNode(myProject, parentNode);
      }
      currentNode.setAutoExpandNode(currentNode == rootElement || parentNode == rootElement);
      nodesMap.put(event.getId(), currentNode);

      if (parentNode != null) {
        parentNode.add(currentNode);
      }

      if (event instanceof StartBuildEvent) {
        String buildTitle = ((StartBuildEvent)event).getBuildTitle();
        currentNode.setTitle(buildTitle);
        currentNode.setAutoExpandNode(true);
        scheduleUpdate(currentNode);
      }
      else if (event instanceof MessageEvent) {
        MessageEvent messageEvent = (MessageEvent)event;
        currentNode.setStartTime(messageEvent.getEventTime());
        currentNode.setEndTime(messageEvent.getEventTime());
        currentNode.setNavigatable(messageEvent.getNavigatable(myProject));
        final MessageEventResult messageEventResult = messageEvent.getResult();
        currentNode.setResult(messageEventResult);
      }
    }
    else {
      currentNode = nodesMap.get(event.getId());
      if (currentNode == null && event instanceof ProgressBuildEvent) {
        currentNode = new ExecutionNode(myProject, parentNode);
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
      final String text = "__" + currentNode.getDuration();
      ApplicationManager.getApplication().invokeLater(() -> {
        int timeColumnWidth = new JLabel(text, SwingConstants.RIGHT).getPreferredSize().width;
        if (myTimeColumnWidth < timeColumnWidth) {
          myTimeColumnWidth = timeColumnWidth;
        }
      });
    }

    if (event instanceof FinishBuildEvent) {
      buildFinishEventNodes((FinishBuildEvent)event, currentNode);
    }
  }

  private void buildFinishEventNodes(@NotNull FinishBuildEvent event, @NotNull ExecutionNode currentNode) {
      String aHint = event.getHint();
      String time = DateFormatUtil.formatDateTime(event.getEventTime());
      aHint = aHint == null ? "at " + time : aHint + " at " + time;
      currentNode.setHint(aHint);
      updateTimeColumnWidth(myTimeColumnWidth);
      if (myDetailsHandler.myExecutionNode == null) {
        ExecutionNode element = myBuildStepsNode;
        ApplicationManager.getApplication().invokeLater(() -> myDetailsHandler.setNode(element));
      }

    if (event.getResult() instanceof FailureResult) {
      buildFailureNodes();
      return;
    }

    scheduleUpdate(currentNode);
  }


  private void buildFailureNodes() {
    ExecutionNode rootElement = getRootElement();
    myBuildStepsNode.setTitle(StringUtil.toTitleCase(myBuildStepsNode.getName()));
    myBuildStepsNode.setHint(rootElement.getHint());
    myBuildStepsNode.setEndTime(rootElement.getEndTime());
    myBuildStepsNode.setResult(new MessageEventResult() {
      @Override
      public MessageEvent.Kind getKind() {
        return MessageEvent.Kind.INFO;
      }
    });
    myBuildStepsNode.setAutoExpandNode(false);
    TreeUtil.collapseAll(myTree, 1);
    selectFirstError();
    scheduleUpdate(myBuildStepsNode);
  }

  private void selectFirstError() {
    ExecutionNode errorNode = (ExecutionNode)getRootElement().getChildAt(0);
    while (errorNode.getChildCount() > 0) {
      errorNode.setAutoExpandNode(true);
      errorNode = ((ExecutionNode)errorNode.getChildAt(0));
    }
    ExecutionNode finalErrorNode = errorNode;
    ApplicationManager.getApplication().invokeLater(() -> myDetailsHandler.setNode(finalErrorNode));
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
    final MessageEvent.Kind eventKind = messageEvent.getKind();
    int indexInParent = parentNode.getChildCount();

    if (eventKind == MessageEvent.Kind.ERROR) {
      // We want to surface errors to the user so error group are attached to the root node
      // and before the build steps nodes
      parentNode = getRootElement();
      indexInParent = myBuildStepNodePosition++;
    }

    ExecutionNode messagesGroupNode =
      getOrCreateMessagesNode(messageEvent, groupNodeId, parentNode, indexInParent, null, group, true, null, null, nodesMap, myProject);

    EventResult groupNodeResult = messagesGroupNode.getResult();
    if (!(groupNodeResult instanceof MessageEventResult) ||
        ((MessageEventResult)groupNodeResult).getKind().compareTo(eventKind) > 0) {
      messagesGroupNode.setResult((MessageEventResult)() -> eventKind);
    }

    ExecutionNode newParent;
    if (messageEvent instanceof FileMessageEvent) {
      newParent = buildSubtreeForFileEvent((FileMessageEvent)messageEvent, groupNodeId, messagesGroupNode);
    }
    else {
      newParent = messagesGroupNode;
    }

    if (eventKind == MessageEvent.Kind.ERROR || eventKind == MessageEvent.Kind.WARNING) {
      SimpleNode p = newParent;
      do {
        ((ExecutionNode)p).reportChildMessageKind(eventKind);
      }
      while ((p = p.getParent()) instanceof ExecutionNode);
    }
    return newParent;
  }

  @NotNull
  private ExecutionNode buildSubtreeForFileEvent(FileMessageEvent messageEvent, String groupNodeId, ExecutionNode messagesGroupNode) {
    ExecutionNode parentNode;
    ExecutionNode fileParentNode = messagesGroupNode;
    FilePosition filePosition = messageEvent.getFilePosition();
    String filePath = FileUtil.toSystemIndependentName(filePosition.getFile().getPath());
    String parentsPath = "";

    String relativePath = FileUtil.getRelativePath(myWorkingDir, filePath, '/');
    if (relativePath != null) {
      String nodeId = groupNodeId + myWorkingDir;
      ExecutionNode workingDirNode = getOrCreateMessagesNode(messageEvent, nodeId, messagesGroupNode, -1, myWorkingDir, null, false,
                                                             () -> AllIcons.Nodes.Module, null, nodesMap, myProject);
      parentsPath = myWorkingDir;
      fileParentNode = workingDirNode;
    }

    VirtualFile sourceRootForFile;
    VirtualFile ioFile = VfsUtil.findFileByIoFile(new File(filePath), false);
    if (ioFile != null &&
        (sourceRootForFile = ProjectFileIndex.SERVICE.getInstance(myProject).getSourceRootForFile(ioFile)) != null) {
      relativePath = FileUtil.getRelativePath(parentsPath, sourceRootForFile.getPath(), '/');
      if (relativePath != null) {
        parentsPath += ("/" + relativePath);
        String contentRootNodeId = groupNodeId + sourceRootForFile.getPath();
        fileParentNode = getOrCreateMessagesNode(messageEvent, contentRootNodeId, fileParentNode, -1, relativePath, null, false,
                                                 () -> getIconForFile(ioFile), null, nodesMap, myProject);
      }
    }

    String fileNodeId = groupNodeId + filePath;
    relativePath = StringUtil.isEmpty(parentsPath) ? filePath : FileUtil.getRelativePath(parentsPath, filePath, '/');
    parentNode = getOrCreateMessagesNode(messageEvent, fileNodeId, fileParentNode, -1, relativePath, null, false,
                                         () -> getIconForPosition(filePosition), messageEvent.getNavigatable(myProject), nodesMap,
                                         myProject);
    return parentNode;
  }

  private Icon getIconForFile(VirtualFile ioFile) {
    return ProjectFileIndex.SERVICE.getInstance(myProject).isInTestSourceContent(ioFile)
           ? AllIcons.Modules.TestRoot
           : AllIcons.Modules.SourceRoot;
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

  @Nullable
  private static Icon getIconForPosition(FilePosition filePosition) {
    VirtualFile file = VfsUtil.findFileByIoFile(filePosition.getFile(), false);
    if (file != null) {
      return file.getFileType().getIcon();
    }
    return null;
  }

  @TestOnly
  JTree getTree() {
    return myTree;
  }

  @NotNull
  private static ExecutionNode getOrCreateMessagesNode(MessageEvent messageEvent,
                                                       String nodeId,
                                                       ExecutionNode parentNode,
                                                       int indexInParent,
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
      if (indexInParent >= 0) {
        parentNode.add(indexInParent, node);
      }
      else {
        parentNode.add(node);
      }
      nodesMap.put(nodeId, node);
    }
    return node;
  }
  private static class DetailsHandler {
    private final ThreeComponentsSplitter mySplitter;
    private final ConsoleView myConsole;
    private final JPanel myPanel;
    @Nullable
    private ExecutionNode myExecutionNode;

    DetailsHandler(Project project,
                   TreeTableTree tree,
                   ThreeComponentsSplitter threeComponentsSplitter) {
      myConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
      mySplitter = threeComponentsSplitter;
      myPanel = new JPanel(new BorderLayout());
      JComponent consoleComponent = myConsole.getComponent();
      AnAction[] consoleActions = myConsole.createConsoleActions();
      consoleComponent.setFocusable(true);
      final Color editorBackground = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
      consoleComponent.setBorder(new CompoundBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT),
                                                    new SideBorder(editorBackground, SideBorder.LEFT)));
      myPanel.add(consoleComponent, BorderLayout.CENTER);
      final ActionToolbar toolbar = ActionManager.getInstance()
        .createActionToolbar("BuildResults", new DefaultActionGroup(consoleActions), false);
      myPanel.add(toolbar.getComponent(), BorderLayout.EAST);
      myPanel.setVisible(false);
      tree.addTreeSelectionListener(e -> {
        TreePath path = e.getPath();
        if (path == null || !e.isAddedPath()) {
          return;
        }
        TreePath selectionPath = tree.getSelectionPath();
        setNode(selectionPath != null ? (DefaultMutableTreeNode)selectionPath.getLastPathComponent() : null);
      });

      Disposer.register(threeComponentsSplitter, myConsole);
    }

    public boolean setNode(@NotNull ExecutionNode node) {
      EventResult eventResult = node.getResult();
      boolean hasChanged = false;

      if (eventResult instanceof FailureResult) {
        myConsole.clear();
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
            myConsole.print("\n\n", ConsoleViewContentType.NORMAL_OUTPUT);
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
        myConsole.clear();
        printDetails(null, details);
        hasChanged = true;
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

    private boolean printDetails(Failure failure, @Nullable String details) {
      return BuildConsoleUtils.printDetails(myConsole, failure, details);
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
