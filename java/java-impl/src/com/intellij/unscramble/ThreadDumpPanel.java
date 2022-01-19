// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.unscramble;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ExporterToTextFile;
import com.intellij.ide.ui.UISettings;
import com.intellij.java.JavaBundle;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

import static com.intellij.icons.AllIcons.Debugger.ThreadStates.*;

/**
 * @author Jeka
 * @author Konstantin Bulenkov
 */
public final class ThreadDumpPanel extends JPanel implements DataProvider {
  private static final Icon PAUSE_ICON_DAEMON = new LayeredIcon(AllIcons.Actions.Pause, Daemon_sign);
  private static final Icon LOCKED_ICON_DAEMON = new LayeredIcon(AllIcons.Debugger.MuteBreakpoints, Daemon_sign);
  private static final Icon RUNNING_ICON_DAEMON = new LayeredIcon(AllIcons.Actions.Resume, Daemon_sign);
  private static final Icon SOCKET_ICON_DAEMON = new LayeredIcon(Socket, Daemon_sign);
  private static final Icon IDLE_ICON_DAEMON = new LayeredIcon(Idle, Daemon_sign);
  private static final Icon EDT_BUSY_ICON_DAEMON = new LayeredIcon(AllIcons.Actions.ProfileCPU, Daemon_sign);
  private static final Icon IO_ICON_DAEMON = new LayeredIcon(AllIcons.Actions.MenuSaveall, Daemon_sign);
  private final JBList<ThreadState> myThreadList;
  private final List<ThreadState> myThreadDump;
  private final List<ThreadState> myMergedThreadDump;
  private final JPanel myFilterPanel;
  private final SearchTextField myFilterField;
  private final ExporterToTextFile myExporterToTextFile;

  public ThreadDumpPanel(Project project, ConsoleView consoleView, DefaultActionGroup toolbarActions, List<ThreadState> threadDump) {
    super(new BorderLayout());
    myThreadDump = threadDump;
    myMergedThreadDump = new ArrayList<>();
    List<ThreadState> copy = new ArrayList<>(myThreadDump);
    for (int i = 0; i < copy.size(); i++) {
      ThreadState state = copy.get(i);
      ThreadState.CompoundThreadState compound = new ThreadState.CompoundThreadState(state);
      myMergedThreadDump.add(compound);
      for (int j = i + 1; j < copy.size(); ) {
        ThreadState toAdd = copy.get(j);
        if (compound.add(toAdd)) {
          copy.remove(j);
        }
        else {
          j++;
        }
      }
    }


    myFilterField = new SearchTextField();
    myFilterField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        updateThreadList();
      }
    });

    myFilterPanel = new JPanel(new BorderLayout());
    myFilterPanel.add(new JLabel(CommonBundle.message("label.filter") + ":"), BorderLayout.WEST);
    myFilterPanel.add(myFilterField);
    myFilterPanel.setVisible(false);

    myThreadList = new JBList<>(new DefaultListModel<>());
    myThreadList.setCellRenderer(new ThreadListCellRenderer());
    myThreadList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myThreadList.addListSelectionListener(new ListSelectionListener() {
      int currentSelectedIndex = -2; // to avoid multiple expensive invocations of printStackTrace()
      @Override
      public void valueChanged(ListSelectionEvent e) {
        int index = myThreadList.getSelectedIndex();
        if (index != currentSelectedIndex) {
          if (index >= 0) {
            ThreadState selection = myThreadList.getModel().getElementAt(index);
            AnalyzeStacktraceUtil.printStacktrace(consoleView, selection.getStackTrace());
          }
          else {
            AnalyzeStacktraceUtil.printStacktrace(consoleView, "");
          }
          currentSelectedIndex = index;
        }
        myThreadList.repaint();
      }
    });

    myExporterToTextFile = createToFileExporter(project, myThreadDump);

    FilterAction filterAction = new FilterAction();
    filterAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND).getShortcutSet(), myThreadList);
    toolbarActions.add(filterAction);
    toolbarActions.add(new CopyToClipboardAction(threadDump, project));
    toolbarActions.add(new SortThreadsAction());
    toolbarActions.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EXPORT_TO_TEXT_FILE));
    toolbarActions.add(new MergeStacktracesAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ThreadDump", toolbarActions, false);
    toolbar.setTargetComponent(consoleView.getComponent());
    add(toolbar.getComponent(), BorderLayout.WEST);

    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(myFilterPanel, BorderLayout.NORTH);
    leftPanel.add(ScrollPaneFactory.createScrollPane(myThreadList, SideBorder.LEFT | SideBorder.RIGHT), BorderLayout.CENTER);

    Splitter splitter = new Splitter(false, 0.3f);
    splitter.setFirstComponent(leftPanel);
    splitter.setSecondComponent(consoleView.getComponent());
    add(splitter, BorderLayout.CENTER);

    new ListSpeedSearch<>(myThreadList).setComparator(new SpeedSearchComparator(false, true));

    updateThreadList();

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

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.EXPORTER_TO_TEXT_FILE.is(dataId)) {
      return myExporterToTextFile;
    }
    return null;
  }

  private void updateThreadList() {
    String text = myFilterPanel.isVisible() ? myFilterField.getText() : "";
    Object selection = myThreadList.getSelectedValue();
    DefaultListModel<ThreadState> model = (DefaultListModel<ThreadState>)myThreadList.getModel();
    model.clear();
    int selectedIndex = 0;
    int index = 0;
    List<ThreadState> threadStates = UISettings.getInstance().getState().getMergeEqualStackTraces() ? myMergedThreadDump : myThreadDump;
    for (ThreadState state : threadStates) {
      if (StringUtil.containsIgnoreCase(state.getStackTrace(), text) || StringUtil.containsIgnoreCase(state.getName(), text)) {
        model.addElement(state);
        if (selection == state) {
          selectedIndex = index;
        }
        index++;
      }
    }
    if (!model.isEmpty()) {
      myThreadList.setSelectedIndex(selectedIndex);
    }
    myThreadList.revalidate();
    myThreadList.repaint();
  }

  private static void highlightOccurrences(String filter, Project project, Editor editor) {
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
    String documentText = editor.getDocument().getText();
    int i = -1;
    while (true) {
      int nextOccurrence = StringUtil.indexOfIgnoreCase(documentText, filter, i + 1);
      if (nextOccurrence < 0) {
        break;
      }
      i = nextOccurrence;
      highlightManager.addOccurrenceHighlight(editor, i, i + filter.length(), attributes,
                                               HighlightManager.HIDE_BY_TEXT_CHANGE, null, null);
    }
  }

  private static Icon getThreadStateIcon(ThreadState threadState) {
    boolean daemon = threadState.isDaemon();
    if (threadState.isSleeping()) {
      return daemon ? PAUSE_ICON_DAEMON : AllIcons.Actions.Pause;
    }
    if (threadState.isWaiting()) {
      return daemon ? LOCKED_ICON_DAEMON : AllIcons.Debugger.MuteBreakpoints;
    }
    if (threadState.getOperation() == ThreadOperation.Socket) {
      return daemon ? SOCKET_ICON_DAEMON : Socket;
    }
    if (threadState.getOperation() == ThreadOperation.IO) {
      return daemon ? IO_ICON_DAEMON : AllIcons.Actions.MenuSaveall;
    }
    if (threadState.isEDT()) {
      if ("idle".equals(threadState.getThreadStateDetail())) {
        return daemon ? IDLE_ICON_DAEMON : Idle;
      }
      return daemon ? EDT_BUSY_ICON_DAEMON : AllIcons.Actions.ProfileCPU;
    }
    return daemon ? RUNNING_ICON_DAEMON : AllIcons.Actions.Resume;
  }

  private enum StateCode {RUN, RUN_IO, RUN_SOCKET, PAUSED, LOCKED, EDT, IDLE}
  private static StateCode getThreadStateCode(ThreadState state) {
    if (state.isSleeping()) return StateCode.PAUSED;
    if (state.isWaiting()) return StateCode.LOCKED;
    if (state.getOperation() == ThreadOperation.Socket) return StateCode.RUN_SOCKET;
    if (state.getOperation() == ThreadOperation.IO) return StateCode.RUN_IO;
    if (state.isEDT()) {
      return "idle".equals(state.getThreadStateDetail()) ? StateCode.IDLE : StateCode.EDT;
    }
    return StateCode.RUN;
  }

  private static SimpleTextAttributes getAttributes(@NotNull ThreadState threadState) {
    if (threadState.isSleeping()) {
      return SimpleTextAttributes.GRAY_ATTRIBUTES;
    }
    if (threadState.isEmptyStackTrace() || threadState.isKnownJDKThread()) {
      return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY.brighter());
    }
    if (threadState.isEDT()) {
      return SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  private static class ThreadListCellRenderer extends ColoredListCellRenderer<ThreadState> {
    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends ThreadState> list, ThreadState threadState, int index, boolean selected, boolean hasFocus) {
      setIcon(getThreadStateIcon(threadState));
      if (!selected) {
        ThreadState selectedThread = list.getSelectedValue();
        if (threadState.isDeadlocked()) {
          setBackground(LightColors.RED);
        }
        else if (selectedThread != null && threadState.isAwaitedBy(selectedThread)) {
          setBackground(JBColor.YELLOW);
        }
        else {
          setBackground(UIUtil.getListBackground());
        }
      }
      SimpleTextAttributes attrs = getAttributes(threadState);
      append(threadState.getName() + " (", attrs);
      String detail = threadState.getThreadStateDetail();
      if (detail == null) {
        detail = threadState.getState();
      }
      if (detail.length() > 30) {
        detail = detail.substring(0, 30) + "...";
      }
      append(detail, attrs);
      append(")", attrs);
      if (threadState.getExtraState() != null) {
        append(" [" + threadState.getExtraState() + "]", attrs);
      }
    }
  }

  public void selectStackFrame(int index) {
    myThreadList.setSelectedIndex(index);
  }

  private final class SortThreadsAction extends DumbAwareAction {
    private final Comparator<ThreadState> BY_TYPE = (o1, o2) -> {
      int c = getThreadStateCode(o1).compareTo(getThreadStateCode(o2));
      if (c == 0) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      } else {
        return c;
      }
    };

    private final Comparator<ThreadState> BY_NAME = (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName());
    private Comparator<ThreadState> COMPARATOR = BY_TYPE;

    private SortThreadsAction() {
      super(JavaBundle.message("sort.threads.by.type"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myThreadDump.sort(COMPARATOR);
      myMergedThreadDump.sort(COMPARATOR);
      updateThreadList();
      COMPARATOR = COMPARATOR == BY_TYPE ? BY_NAME : BY_TYPE;
      update(e);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setIcon(COMPARATOR == BY_TYPE ? AllIcons.ObjectBrowser.SortByType : AllIcons.ObjectBrowser.Sorted);
      e.getPresentation().setText(COMPARATOR == BY_TYPE ? JavaBundle.message("sort.threads.by.type") :
                                  JavaBundle.message("sort.threads.by.name"));
    }
  }
  private static final class CopyToClipboardAction extends DumbAwareAction {
    private static final NotificationGroup GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Analyze thread dump");
    private final List<? extends ThreadState> myThreadDump;
    private final Project myProject;

    private CopyToClipboardAction(List<? extends ThreadState> threadDump, Project project) {
      super(JavaBundle.message("action.text.copy.to.clipboard"), JavaBundle.message("action.description.copy.whole.thread.dump.to.clipboard"), PlatformIcons.COPY_ICON);
      myThreadDump = threadDump;
      myProject = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      StringBuilder buf = new StringBuilder();
      buf.append("Full thread dump").append("\n\n");
      for (ThreadState state : myThreadDump) {
        buf.append(state.getStackTrace()).append("\n\n");
      }
      CopyPasteManager.getInstance().setContents(new StringSelection(buf.toString()));

      GROUP.createNotification(JavaBundle.message("notification.text.full.thread.dump.was.successfully.copied.to.clipboard"), MessageType.INFO).notify(myProject);
    }
  }

  private final class FilterAction extends ToggleAction implements DumbAware {

    private FilterAction() {
      super(CommonBundle.messagePointer("action.text.filter"), JavaBundle.messagePointer(
        "action.description.show.only.threads.containing.a.specific.string"), AllIcons.General.Filter);
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
      updateThreadList();
    }
  }

  private final class MergeStacktracesAction extends ToggleAction implements DumbAware {
    private MergeStacktracesAction() {
      super(JavaBundle.messagePointer("action.text.merge.identical.stacktraces"), JavaBundle.messagePointer(
        "action.description.group.threads.with.identical.stacktraces"), AllIcons.Actions.Collapseall);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return UISettings.getInstance().getState().getMergeEqualStackTraces();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      UISettings.getInstance().getState().setMergeEqualStackTraces(state);
      updateThreadList();
    }
  }

  public static ExporterToTextFile createToFileExporter(Project project, List<? extends ThreadState> threadStates) {
    return new MyToFileExporter(project, threadStates);
  }

  private static final class MyToFileExporter implements ExporterToTextFile {
    private final Project myProject;
    private final List<? extends ThreadState> myThreadStates;

    private MyToFileExporter(Project project, List<? extends ThreadState> threadStates) {
      myProject = project;
      myThreadStates = threadStates;
    }

    @NotNull
    @Override
    public String getReportText() {
      StringBuilder sb = new StringBuilder();
      for (ThreadState state : myThreadStates) {
        sb.append(state.getStackTrace()).append("\n\n");
      }
      return sb.toString();
    }

    @NonNls
    private static final String DEFAULT_REPORT_FILE_NAME = "threads_report.txt";

    @NotNull
    @Override
    public String getDefaultFilePath() {
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
