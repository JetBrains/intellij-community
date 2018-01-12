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
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TransferToEDTQueue;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

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
  private final String myWorkingDir;
  private volatile int myTimeColumnWidth;
  private final AtomicBoolean myDisposed = new AtomicBoolean();
  private final TransferToEDTQueue<Runnable> myLaterInvocator =
    TransferToEDTQueue.createRunnableMerger("BuildTreeConsoleView later invocator");

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
    final ExecutionNode rootNode = new ExecutionNode(myProject, null);
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
    EditSourceOnDoubleClickHandler.install(treeTable);
    EditSourceOnEnterKeyHandler.install(treeTable, null);

    TreeTableTree tree = treeTable.getTree();
    final TreeCellRenderer treeCellRenderer = tree.getCellRenderer();
    tree.setCellRenderer(new TreeCellRenderer() {
      @Override
      public Component getTreeCellRendererComponent(JTree tree,
                                                    Object value,
                                                    boolean selected,
                                                    boolean expanded,
                                                    boolean leaf,
                                                    int row,
                                                    boolean hasFocus) {
        final Component rendererComponent =
          treeCellRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        if (rendererComponent instanceof SimpleColoredComponent) {
          final Color bg = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
          final Color fg = selected ? UIUtil.getTreeSelectionForeground() : UIUtil.getTreeForeground();
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
      }
    });
    new TreeTableSpeedSearch(treeTable).setComparator(new SpeedSearchComparator(false));
    treeTable.setTableHeader(null);

    myTimeColumn = treeTable.getColumnModel().getColumn(1);
    myTimeColumn.setResizable(false);
    updateTimeColumnWidth("Running for " + StringUtil.formatDuration(11111L), true);

    TreeUtil.installActions(tree);
    myTreeStructure = new SimpleTreeStructure.Impl(rootNode);

    myBuilder = new SimpleTreeBuilder(tree, model, myTreeStructure, null);
    Disposer.register(this, myBuilder);
    myBuilder.initRootNode();
    myBuilder.updateFromRoot();

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

    myProgressAnimator = new ExecutionNodeProgressAnimator(this);
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
    myDisposed.set(true);
  }

  public boolean isDisposed() {
    return myDisposed.get();
  }

  @Override
  public void onEvent(BuildEvent event) {
    ExecutionNode parentNode = event.getParentId() == null ? null : nodesMap.get(event.getParentId());
    ExecutionNode currentNode = nodesMap.get(event.getId());
    if (event instanceof StartEvent || event instanceof MessageEvent) {
      ExecutionNode rootElement = getRootElement();
      if (currentNode == null) {
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
        myProgressAnimator.startMovie();
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
      int timeColumnWidth = new JLabel("__" + currentNode.getDuration(), SwingConstants.RIGHT).getPreferredSize().width;
      if (myTimeColumnWidth < timeColumnWidth) {
        myTimeColumnWidth = timeColumnWidth;
      }
    }
    else {
      scheduleUpdate(currentNode);
      if (event instanceof StartEvent) {
        myProgressAnimator.addNode(currentNode);
      }
    }

    if (event instanceof FinishBuildEvent) {
      String aHint = event.getHint();
      String time = DateFormatUtil.formatDateTime(event.getEventTime());
      aHint = aHint == null ? "  at " + time : aHint + "  at " + time;
      currentNode.setHint(aHint);
      updateTimeColumnWidth(myTimeColumnWidth);
      if (myDetailsHandler.myExecutionNode == null) {
        myDetailsHandler.setNode(getRootElement());
      }

      if (((FinishBuildEvent)event).getResult() instanceof FailureResult) {
        JTree tree = myBuilder.getTree();
        if (tree != null && !tree.isRootVisible()) {
          ExecutionNode rootElement = getRootElement();
          ExecutionNode resultNode = new ExecutionNode(myProject, rootElement);
          resultNode.setName(StringUtil.toTitleCase(rootElement.getName()));
          resultNode.setHint(rootElement.getHint());
          resultNode.setEndTime(rootElement.getEndTime());
          resultNode.setStartTime(rootElement.getStartTime());
          resultNode.setResult(rootElement.getResult());
          resultNode.setTooltip(rootElement.getTooltip());
          rootElement.add(resultNode);

          scheduleUpdate(resultNode);
        }
      }
      myProgressAnimator.stopMovie();
      myBuilder.updateFromRoot();
    }
  }

  void scheduleUpdate(ExecutionNode executionNode) {
    SimpleNode node = executionNode.getParent() == null ? executionNode : executionNode.getParent();
    final Update update = new Update(node) {
      @Override
      public void run() {
        myBuilder.queueUpdateFrom(node, false, true);
      }
    };
    myLaterInvocator.offerIfAbsent(update);
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
      messagesGroupNode.setResult(new MessageEventResult() {
        @Override
        public MessageEvent.Kind getKind() {
          return eventKind;
        }
      });
    }
    if (messageEvent instanceof FileMessageEvent) {
      ExecutionNode fileParentNode = messagesGroupNode;
      FilePosition filePosition = ((FileMessageEvent)messageEvent).getFilePosition();
      String filePath = FileUtil.toSystemIndependentName(filePosition.getFile().getPath());
      String parentsPath = "";

      String relativePath = FileUtil.getRelativePath(myWorkingDir, filePath, '/');
      if (relativePath != null) {
        String nodeId = group.hashCode() + myWorkingDir;
        ExecutionNode workingDirNode = getOrCreateMessagesNode(messageEvent, nodeId, messagesGroupNode, myWorkingDir, null, false,
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
          String contentRootNodeId = group.hashCode() + sourceRootForFile.getPath();
          fileParentNode = getOrCreateMessagesNode(messageEvent, contentRootNodeId, fileParentNode, relativePath, null, false,
                                                   () -> ProjectFileIndex.SERVICE.getInstance(myProject).isInTestSourceContent(ioFile)
                                                         ? AllIcons.Modules.TestRoot
                                                         : AllIcons.Modules.SourceRoot, null, nodesMap, myProject);
        }
      }

      String fileNodeId = group.hashCode() + filePath;
      relativePath = StringUtil.isEmpty(parentsPath) ? filePath : FileUtil.getRelativePath(parentsPath, filePath, '/');
      parentNode = getOrCreateMessagesNode(messageEvent, fileNodeId, fileParentNode, relativePath, null, false,
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
    }
    return parentNode;
  }

  @NotNull
  private static ExecutionNode getOrCreateMessagesNode(MessageEvent messageEvent,
                                                       String nodeId,
                                                       ExecutionNode parentNode,
                                                       String nodeName,
                                                       String nodeTitle,
                                                       boolean autoExpandNode,
                                                       @Nullable Supplier<Icon> iconProvider,
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
      if (!(eventResult instanceof FailureResult)) return false;
      List<? extends Failure> failures = ((FailureResult)eventResult).getFailures();
      if (failures.isEmpty()) return false;
      myConsole.clear();

      boolean hasChanged = false;
      for (Iterator<? extends Failure> iterator = failures.iterator(); iterator.hasNext(); ) {
        Failure failure = iterator.next();
        if (!printFailure(failure)) continue;
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

    private boolean printFailure(Failure failure) {
      return BuildConsoleUtils.printFailure(myConsole, failure);
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
