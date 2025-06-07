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
import com.intellij.openapi.actionSystem.*;
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
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Jeka
 * @author Konstantin Bulenkov
 */
public final class ThreadDumpPanel extends JPanel implements NoStackTraceFoldingPanel {
  private final JBList<DumpItem> myThreadList;
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
    myThreadList = createThreadList(consoleView);

    configureToolbar(project, consoleView, toolbarActions);

    updateThreadDumpItemList();

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

  private SearchTextField createSearchTextField() {
    SearchTextField searchTextField = new SearchTextField();
    searchTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateThreadDumpItemList();
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

  private static JBList<DumpItem> createThreadList(ConsoleView consoleView) {
    JBList<DumpItem> threadList = new JBList<>(new DefaultListModel<>());
    threadList.setCellRenderer(new ThreadListCellRenderer());
    threadList.setEmptyText(JavaFrontbackBundle.message("thread.dump.loading.text"));
    threadList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    threadList.addListSelectionListener(new ListSelectionListener() {
      int currentSelectedIndex = -2; // to avoid multiple expensive invocations of printStackTrace()
      @Override
      public void valueChanged(ListSelectionEvent e) {
        int index = threadList.getSelectedIndex();
        if (index != currentSelectedIndex) {
          if (index >= 0) {
            DumpItem selection = threadList.getModel().getElementAt(index);
            AnalyzeStacktraceUtil.printStacktrace(consoleView, selection.getStackTrace());
          }
          else {
            AnalyzeStacktraceUtil.printStacktrace(consoleView, "");
          }
          currentSelectedIndex = index;
        }
        threadList.repaint();
      }
    });
    ListSpeedSearch
      .installOn(threadList, DumpItem::getName)
      .setComparator(new SpeedSearchComparator(false, true));
    return threadList;
  }

  private void configureToolbar(Project project, ConsoleView consoleView, DefaultActionGroup toolbarActions) {
    FilterAction filterAction = new FilterAction();
    filterAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND).getShortcutSet(), myThreadList);
    toolbarActions.add(filterAction);
    toolbarActions.add(new CopyToClipboardAction(project));
    toolbarActions.add(new SortThreadsAction());
    toolbarActions.add(new ExportToTextFileToolbarAction(createDumpToFileExporter(project, myThreadDump)));
    toolbarActions.add(new MergeStacktracesAction());

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ThreadDump", toolbarActions, false);
    toolbar.setTargetComponent(consoleView.getComponent());
    add(toolbar.getComponent(), BorderLayout.WEST);

    JPanel leftPanel = new JPanel(new BorderLayout());
    JPanel northPanel = new JPanel(new BorderLayout());

    northPanel.add(myNotificationPanel, BorderLayout.NORTH);
    northPanel.add(myFilterPanel, BorderLayout.SOUTH);
    leftPanel.add(northPanel, BorderLayout.NORTH);
    leftPanel.add(ScrollPaneFactory.createScrollPane(myThreadList, SideBorder.LEFT | SideBorder.RIGHT), BorderLayout.CENTER);

    Splitter splitter = new Splitter(false, 0.3f);
    splitter.setFirstComponent(leftPanel);
    splitter.setSecondComponent(consoleView.getComponent());
    add(splitter, BorderLayout.CENTER);
  }

  private void sortAndUpdateThreadDumpItemList() {
    myThreadDump.sort(currentComparator);
    myMergedThreadDump.sort(currentComparator);
    updateThreadDumpItemList();
  }

  private void updateThreadDumpItemList() {
    String text = myFilterPanel.isVisible() ? myFilterField.getText() : "";
    Object selection = myThreadList.getSelectedValue();
    int selectedIndex = 0;
    int index = 0;
    ArrayList<DumpItem> filteredThreadStates = new ArrayList<>();
    boolean useMerged = UISettings.getInstance().getState().getMergeEqualStackTraces();
    List<DumpItem> threadStates = useMerged ? myMergedThreadDump : myThreadDump;
    for (DumpItem state : threadStates) {
      if (StringUtil.containsIgnoreCase(state.getStackTrace(), text) || StringUtil.containsIgnoreCase(state.getName(), text)) {
        filteredThreadStates.add(state);
        if (selection == state) {
          selectedIndex = index;
        }
        index++;
      }
    }

    // Add all of them in a single call, otherwise it works too slow recalculating UI layout.
    DefaultListModel<DumpItem> model = (DefaultListModel<DumpItem>)myThreadList.getModel();
    model.clear();
    model.addAll(filteredThreadStates);
    int truncated = useMerged ? myMergedDumpItemsTruncated : myDumpItemsTruncated;
    if (truncated > 0) {
      myNotificationPanel.text(JavaFrontbackBundle.message("truncated.dump.notification", threadStates.size()));
      myNotificationPanel.setVisible(true);
    }
    else {
      myNotificationPanel.setVisible(false);
    }
    if (!model.isEmpty()) {
      myThreadList.setSelectedIndex(selectedIndex);
    }
    myThreadList.revalidate();
    myThreadList.repaint();
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

  private static class ThreadListCellRenderer extends ColoredListCellRenderer<DumpItem> {
    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends DumpItem> list, DumpItem threadState, int index, boolean selected, boolean hasFocus) {
      setIcon(threadState.getIcon());
      if (!selected) {
        DumpItem selectedThread = list.getSelectedValue();
        setBackground(getBackgroundColor(threadState, selectedThread));
      }
      SimpleTextAttributes attrs = threadState.getAttributes();
      append(threadState.getName(), attrs);
      append(threadState.getStateDesc(), attrs);
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
    myThreadList.setSelectedIndex(index);
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
      updateThreadDumpItemList();
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
      updateThreadDumpItemList();
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
