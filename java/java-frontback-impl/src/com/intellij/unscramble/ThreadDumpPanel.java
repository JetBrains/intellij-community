// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.NoStackTraceFoldingPanel;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ExporterToTextFile;
import com.intellij.ide.actions.ExportToTextFileToolbarAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.java.frontback.impl.JavaFrontbackBundle;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.threadDumpParser.ThreadState;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.LightColors;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * @author Jeka
 * @author Konstantin Bulenkov
 */
public final class ThreadDumpPanel extends JPanel implements NoStackTraceFoldingPanel {
  private final Tree myThreadTree;
  private final EditorNotificationPanel myNotificationPanel = new EditorNotificationPanel(EditorNotificationPanel.Status.Info);
  private final List<DumpItem> myThreadDump;
  private final List<DumpItem> myMergedThreadDump;
  private Comparator<DumpItem> currentComparator = DumpItem.BY_INTEREST;
  private final JPanel myFilterPanel;
  private final SearchTextField myFilterField;
  private int myDumpItemsTruncated = 0;
  private int myMergedDumpItemsTruncated = 0;

  public ThreadDumpPanel(Project project, ConsoleView consoleView, DefaultActionGroup toolbarActions, List<ThreadState> threadDump) {
    this(project, consoleView, toolbarActions, DumpItemKt.toDumpItems(threadDump), false);
  }

  @ApiStatus.Internal
  public static ThreadDumpPanel createFromDumpItems(Project project, ConsoleView consoleView, DefaultActionGroup toolbarActions, List<MergeableDumpItem> dumpItems) {
    return new ThreadDumpPanel(project, consoleView, toolbarActions, dumpItems, true);
  }

  @ApiStatus.Internal
  public void addDumpItems(List<DumpItem> threadDump,
                           int dumpItemsTruncated,
                           List<DumpItem> mergedThreadDump,
                           int mergedDumpItemsTruncated) {
    myThreadDump.addAll(threadDump);
    myDumpItemsTruncated += dumpItemsTruncated;
    myMergedThreadDump.addAll(mergedThreadDump);
    myMergedDumpItemsTruncated += mergedDumpItemsTruncated;
    sortAndUpdateThreadDumpItemList();
  }

  private ThreadDumpPanel(Project project, ConsoleView consoleView, DefaultActionGroup toolbarActions, List<MergeableDumpItem> dumpItems, boolean fromDumpItems) {
    super(new BorderLayout());
    myThreadDump = new ArrayList<>(dumpItems);
    myMergedThreadDump = CompoundDumpItem.mergeThreadDumpItems(dumpItems);

    myFilterField = createSearchTextField();
    myFilterPanel = createFilterPanel();
    myThreadTree = createThreadsTree(consoleView);

    configureToolbar(project, consoleView, toolbarActions);

    updateThreadsTree();

    Editor editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext(consoleView.getPreferredFocusableComponent()));
    if (editor != null) {
      editor.getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent e) {
          String filter = myFilterField.getText();
          if (StringUtil.isNotEmpty(filter)) {
            highlightOccurrences(filter, project, editor);
          }
        }
      }, consoleView);
    }
  }

  @ApiStatus.Internal
  Tree getTree() {
    return myThreadTree;
  }

  private SearchTextField createSearchTextField() {
    SearchTextField searchTextField = new SearchTextField();
    searchTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateThreadsTree();
      }
    });
    return searchTextField;
  }

  private JPanel createFilterPanel() {
    JPanel filterPanel = new JPanel(new BorderLayout());
    filterPanel.add(new JLabel(CommonBundle.message("label.filter") + ":"), BorderLayout.WEST);
    filterPanel.add(myFilterField);
    filterPanel.setVisible(false);
    return filterPanel;
  }

  private static Tree createThreadsTree(ConsoleView consoleView) {
    Tree threadTree = new Tree();
    threadTree.setName("Thread Dump");
    threadTree.setCellRenderer(new ThreadTreeCellRenderer());
    threadTree.setRootVisible(false);
    threadTree.getEmptyText().setText(JavaFrontbackBundle.message("thread.dump.loading.text"));
    threadTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    threadTree.addTreeSelectionListener(new TreeSelectionListener() {
      DumpItem currentlySelectedItem = null; // to avoid multiple expensive invocations of printStackTrace()
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        TreePath path = e.getPath();
        if (threadTree.isPathSelected(path)) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
          if (node.getUserObject() instanceof DumpItem selection && selection != currentlySelectedItem) {
            AnalyzeStacktraceUtil.printStacktrace(consoleView, selection.getStackTrace());
            currentlySelectedItem = selection;
          }
        } else {
          AnalyzeStacktraceUtil.printStacktrace(consoleView, "");
        }
        threadTree.repaint();
      }
    });
    TreeSpeedSearch
      .installOn(threadTree, true, path -> {
      var node = (DefaultMutableTreeNode)path.getLastPathComponent();
      return node.getUserObject() instanceof DumpItem item ? item.getName() : "";
      })
      .setComparator(new SpeedSearchComparator(false, true));
    return threadTree;
  }

  private void configureToolbar(Project project, ConsoleView consoleView, DefaultActionGroup toolbarActions) {
    FilterAction filterAction = new FilterAction();
    filterAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND).getShortcutSet(), myThreadTree);
    toolbarActions.add(filterAction);
    toolbarActions.add(new CopyToClipboardAction(project));
    toolbarActions.add(new SortThreadsAction());
    toolbarActions.add(new ExportToTextFileToolbarAction(createDumpToFileExporter(project, myThreadDump)));
    toolbarActions.add(new MergeStacktracesAction());
    toolbarActions.add(new ShowDumpItemGroups());
    toolbarActions.add(new ShowOnlyPlatformThreads());

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ThreadDump", toolbarActions, false);
    toolbar.setTargetComponent(consoleView.getComponent());
    add(toolbar.getComponent(), BorderLayout.WEST);

    JPanel leftPanel = new JPanel(new BorderLayout());
    JPanel northPanel = new JPanel(new BorderLayout());

    northPanel.add(myNotificationPanel, BorderLayout.NORTH);
    northPanel.add(myFilterPanel, BorderLayout.SOUTH);
    leftPanel.add(northPanel, BorderLayout.NORTH);
    leftPanel.add(ScrollPaneFactory.createScrollPane(myThreadTree, SideBorder.LEFT | SideBorder.RIGHT), BorderLayout.CENTER);

    Splitter splitter = new Splitter(false, 0.3f);
    splitter.setFirstComponent(leftPanel);
    splitter.setSecondComponent(consoleView.getComponent());
    add(splitter, BorderLayout.CENTER);
  }

  private void sortAndUpdateThreadDumpItemList() {
    myThreadDump.sort(currentComparator);
    myMergedThreadDump.sort(currentComparator);
    updateThreadsTree();
  }

  private void updateThreadsTree() {
    String text = myFilterPanel.isVisible() ? myFilterField.getText() : "";
    var path = myThreadTree.getSelectionPath();
    var selection = path != null ? (DumpItem)((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject() : null;
    int selectedIndex = 0;
    int index = 0;
    ArrayList<DumpItem> filteredThreadStates = new ArrayList<>();
    var uiSettings = UISettings.getInstance().getState();
    boolean useMerged = uiSettings.getMergeEqualStackTraces();
    boolean showVirtualThreadContainers = uiSettings.getShowVirtualThreadContainers();
    boolean showOnlyPlatformThreads = uiSettings.getShowOnlyPlatformThreads();
    List<DumpItem> threadStates = useMerged ? myMergedThreadDump : myThreadDump;

    // Add all of them in a single call, otherwise it works too slow recalculating UI layout.
    var model = (DefaultTreeModel)myThreadTree.getModel();
    var treeRoot = ((DefaultMutableTreeNode)model.getRoot());

    if (treeRoot != null) {
      treeRoot.removeAllChildren();

      if (showVirtualThreadContainers && !showOnlyPlatformThreads) {
        // Build map from parent item name to the list of it's child items
        var rootItems = new ArrayList<DumpItem>();
        var parentIdToChildren = new HashMap<Long, List<DumpItem>>();
        for (var item : threadStates) {
          var parentId = item.getParentTreeId();
          if (parentId != null) {
            parentIdToChildren.computeIfAbsent(parentId, k -> new ArrayList<>()).add(item);
          } else {
            rootItems.add(item);
          }
        }

        // Build thread dump tree
        for (var rootItem : rootItems) {
          var rootNode = new DefaultMutableTreeNode(rootItem);
          treeRoot.add(rootNode);
          if (!parentIdToChildren.isEmpty()) {
            buildDumpItemsTree(rootNode, parentIdToChildren);
          }
        }
      } else {
        // Show flat dump
        for (DumpItem state : threadStates) {
          if (StringUtil.containsIgnoreCase(state.getStackTrace(), text) || StringUtil.containsIgnoreCase(state.getName(), text)) {
            filteredThreadStates.add(state);
            if (selection == state) {
              selectedIndex = index;
            }
            index++;
          }
        }

        for (DumpItem threadState : filteredThreadStates) {
          if (threadState.isContainer()) continue;
          if (showOnlyPlatformThreads && threadState.getCanBeHidden()) continue;
          treeRoot.add(new DefaultMutableTreeNode(threadState));
        }
      }
    }
    model.reload();

    int truncated = useMerged ? myMergedDumpItemsTruncated : myDumpItemsTruncated;
    if (truncated > 0) {
      myNotificationPanel.text(JavaFrontbackBundle.message("truncated.dump.notification", threadStates.size()));
      myNotificationPanel.setVisible(true);
    }
    else {
      myNotificationPanel.setVisible(false);
    }
    if (treeRoot != null && treeRoot.getChildCount() > 0) {
      myThreadTree.setSelectionRow(selectedIndex);
    }
    myThreadTree.revalidate();
    myThreadTree.repaint();
  }

  private static void buildDumpItemsTree(DefaultMutableTreeNode currentNode, HashMap<Long, List<DumpItem>> parentIdToChildren) {
    var currentDumpItem = (DumpItem)currentNode.getUserObject();
    var childItems = parentIdToChildren.get(currentDumpItem.getTreeId());
    if (childItems == null) {
      if (currentDumpItem.isContainer()) currentNode.removeFromParent(); // do not add empty containers to the tree
      return;
    }
    for (var item : childItems) {
      var childNode = new DefaultMutableTreeNode(item);
      currentNode.add(childNode);
      buildDumpItemsTree(childNode, parentIdToChildren);
    }
  }

  private static void highlightOccurrences(String filter, Project project, Editor editor) {
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    String documentText = editor.getDocument().getText();
    int i = -1;
    while (true) {
      int nextOccurrence = StringUtil.indexOfIgnoreCase(documentText, filter, i + 1);
      if (nextOccurrence < 0) {
        break;
      }
      i = nextOccurrence;
      highlightManager.addOccurrenceHighlight(editor, i, i + filter.length(), EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES,
                                               HighlightManager.HIDE_BY_TEXT_CHANGE, null);
    }
  }

  private static class ThreadTreeCellRenderer extends ColoredTreeCellRenderer {
    @Nls private String iconToolTip;

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      var node = (DefaultMutableTreeNode)value;
      if (node.getUserObject() instanceof DumpItem dumpItem) {
        setIcon((dumpItem).getIcon());
        iconToolTip = dumpItem.getIconToolTip();
        if (selected) {
          var selectedNode = (DefaultMutableTreeNode)tree.getLastSelectedPathComponent();
          var selectedThread = (DumpItem)selectedNode.getUserObject();
          setBackground(getBackgroundColor(dumpItem, selectedThread));
        }
        SimpleTextAttributes attrs = dumpItem.getAttributes();
        append(dumpItem.getName(), attrs);
        append(dumpItem.getStateDesc(), attrs);
      }
    }

    @Override
    protected String getIconToolTipText() {
      return iconToolTip;
    }

    private static @NotNull Color getBackgroundColor(DumpItem threadState, DumpItem selectedThread) {
      if (threadState.isDeadLocked()) {
        return LightColors.RED;
      }
      else if (selectedThread != null && threadState.getAwaitingDumpItems().contains(selectedThread)) {
        return LightColors.YELLOW;
      }
      else {
        return UIUtil.getListBackground();
      }
    }
  }

  public void selectStackFrame(int index) {
    myThreadTree.setSelectionRow(index);
  }

  private final class SortThreadsAction extends DumbAwareAction {

    private SortThreadsAction() {
      super(JavaFrontbackBundle.message("sort.threads.by.interest.level"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      currentComparator = currentComparator == DumpItem.BY_INTEREST ? DumpItem.BY_NAME : DumpItem.BY_INTEREST;
      update(e);
      sortAndUpdateThreadDumpItemList();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setIcon(currentComparator == DumpItem.BY_INTEREST ? AllIcons.ObjectBrowser.Sorted : AllIcons.ObjectBrowser.SortByType);
      e.getPresentation().setText(currentComparator == DumpItem.BY_INTEREST ? JavaFrontbackBundle.message("sort.threads.by.name") :
                                  JavaFrontbackBundle.message("sort.threads.by.interest.level")                                  );
    }
  }
  private final class CopyToClipboardAction extends DumbAwareAction {
    private static final NotificationGroup GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Analyze thread dump");
    private final Project myProject;

    private CopyToClipboardAction(Project project) {
      super(JavaFrontbackBundle.message("action.text.copy.to.clipboard"), JavaFrontbackBundle.message("action.description.copy.whole.thread.dump.to.clipboard"), PlatformIcons.COPY_ICON);
      myProject = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      boolean isTruncated = myDumpItemsTruncated > 0;
      StringBuilder buf = new StringBuilder();
      String firstLine = isTruncated ? "Truncated thread dump" : "Full thread dump";
      buf.append(firstLine).append("\n\n");
      for (DumpItem state : myThreadDump) {
        buf.append(state.getStackTrace()).append("\n\n");
      }
      CopyPasteManager.getInstance().setContents(new StringSelection(buf.toString()));

      String message = isTruncated
                       ? JavaFrontbackBundle.message("notification.text.truncated.thread.dump.was.successfully.copied.to.clipboard")
                       : JavaFrontbackBundle.message("notification.text.full.thread.dump.was.successfully.copied.to.clipboard");
      GROUP.createNotification(message, MessageType.INFO).notify(myProject);
    }
  }

  private final class FilterAction extends ToggleAction implements DumbAware {

    private FilterAction() {
      super(CommonBundle.messagePointer("action.text.filter"), JavaFrontbackBundle.lazyMessage(
        "action.description.show.only.threads.containing.a.specific.string"), AllIcons.General.Filter);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myFilterPanel.isVisible();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myFilterPanel.setVisible(state);
      if (state) {
        IdeFocusManager.getInstance(getEventProject(e)).requestFocus(myFilterField, true);
        myFilterField.selectText();
      }
      updateThreadsTree();
    }
  }

  private final class MergeStacktracesAction extends ToggleAction implements DumbAware {
    private MergeStacktracesAction() {
      super(JavaFrontbackBundle.lazyMessage("action.text.merge.identical.stacktraces"), JavaFrontbackBundle.lazyMessage(
        "action.description.group.threads.with.identical.stacktraces"), AllIcons.Actions.Collapseall);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return UISettings.getInstance().getState().getMergeEqualStackTraces();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      UISettings.getInstance().getState().setMergeEqualStackTraces(state);
      updateThreadsTree();
    }
  }

  private final class ShowDumpItemGroups extends ToggleAction implements DumbAware {
    private ShowDumpItemGroups() {
      super(JavaFrontbackBundle.lazyMessage("action.text.group.dump.items"), JavaFrontbackBundle.lazyMessage(
        "action.description.group.dump.items"), AllIcons.Hierarchy.Subtypes);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return UISettings.getInstance().getState().getShowVirtualThreadContainers();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      UISettings.getInstance().getState().setShowVirtualThreadContainers(state);
      updateThreadsTree();
    }
  }

  private final class ShowOnlyPlatformThreads extends ToggleAction implements DumbAware {
    private ShowOnlyPlatformThreads() {
      super(JavaFrontbackBundle.lazyMessage("action.text.show.only.platform.threads"), JavaFrontbackBundle.lazyMessage(
        "action.description.show.only.platform.threads"), AllIcons.Debugger.Threads);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return UISettings.getInstance().getState().getShowOnlyPlatformThreads();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      UISettings.getInstance().getState().setShowOnlyPlatformThreads(state);
      updateThreadsTree();
    }
  }

  private static ExporterToTextFile createDumpToFileExporter(Project project, List<DumpItem> dumpItems) {
    return new MyToFileExporter(project, dumpItems);
  }

  public static ExporterToTextFile createToFileExporter(Project project, List<ThreadState> threadStates) {
    return new MyToFileExporter(project, DumpItemKt.toDumpItems(threadStates));
  }

  private static final class MyToFileExporter implements ExporterToTextFile {
    private final Project myProject;
    private final List<? extends DumpItem> myThreadStates;

    private MyToFileExporter(Project project, List<? extends DumpItem> threadStates) {
      myProject = project;
      myThreadStates = threadStates;
    }

    @Override
    public @NotNull String getReportText() {
      StringBuilder sb = new StringBuilder();
      for (DumpItem state : myThreadStates) {
        sb.append(state.getStackTrace()).append("\n\n");
      }
      return sb.toString();
    }

    private static final @NonNls String DEFAULT_REPORT_FILE_NAME = "threads_report.txt";

    @Override
    public @NotNull String getDefaultFilePath() {
      VirtualFile baseDir = myProject.getBaseDir();
      if (baseDir != null) {
        return baseDir.getPresentableUrl() + File.separator + DEFAULT_REPORT_FILE_NAME;
      }
      return "";
    }

    @Override
    public boolean canExport() {
      return !myThreadStates.isEmpty();
    }
  }
}
