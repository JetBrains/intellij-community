/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.unscramble;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ExporterToTextFile;
import com.intellij.ide.ui.UISettings;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
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
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.*;
import java.util.List;

import static com.intellij.icons.AllIcons.Debugger.ThreadStates.*;

/**
 * @author Jeka
 * @author Konstantin Bulenkov
 */
public class ThreadDumpPanel extends JPanel implements DataProvider {
  private static final Icon PAUSE_ICON_DAEMON = new LayeredIcon(Paused, Daemon_sign);
  private static final Icon LOCKED_ICON_DAEMON = new LayeredIcon(Locked, Daemon_sign);
  private static final Icon RUNNING_ICON_DAEMON = new LayeredIcon(Running, Daemon_sign);
  private static final Icon SOCKET_ICON_DAEMON = new LayeredIcon(Socket, Daemon_sign);
  private static final Icon IDLE_ICON_DAEMON = new LayeredIcon(Idle, Daemon_sign);
  private static final Icon EDT_BUSY_ICON_DAEMON = new LayeredIcon(EdtBusy, Daemon_sign);
  private static final Icon IO_ICON_DAEMON = new LayeredIcon(IO, Daemon_sign);
  private final JBList myThreadList;
  private final List<ThreadState> myThreadDump;
  private final List<ThreadState> myMergedThreadDump;
  private final JPanel myFilterPanel;
  private final SearchTextField myFilterField;
  private final ExporterToTextFile myExporterToTextFile;

  public ThreadDumpPanel(final Project project, final ConsoleView consoleView, final DefaultActionGroup toolbarActions, final List<ThreadState> threadDump) {
    super(new BorderLayout());
    myThreadDump = threadDump;
    myMergedThreadDump = new ArrayList<>();
    List<ThreadState> copy = new ArrayList<>(myThreadDump);
    for (int i = 0; i < copy.size(); i++) {
      ThreadState state = copy.get(i);
      ThreadState.CompoundThreadState compound = new ThreadState.CompoundThreadState(state);
      myMergedThreadDump.add(compound);
      for (int j = i+1; j < copy.size(); j++) {
        ThreadState toAdd = copy.get(j);
        if (compound.add(toAdd)) {
          copy.remove(j);
        }
      }
    }


    myFilterField = new SearchTextField();
    myFilterField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateThreadList();
      }
    });

    myFilterPanel = new JPanel(new BorderLayout());
    myFilterPanel.add(new JLabel("Filter:"), BorderLayout.WEST);
    myFilterPanel.add(myFilterField);
    myFilterPanel.setVisible(false);

    myThreadList = new JBList(new DefaultListModel());
    myThreadList.setCellRenderer(new ThreadListCellRenderer());
    myThreadList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myThreadList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        int index = myThreadList.getSelectedIndex();
        if (index >= 0) {
          ThreadState selection = (ThreadState)myThreadList.getModel().getElementAt(index);
          AnalyzeStacktraceUtil.printStacktrace(consoleView, selection.getStackTrace());
        }
        else {
          AnalyzeStacktraceUtil.printStacktrace(consoleView, "");
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
    add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false).getComponent(), BorderLayout.WEST);

    JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(myFilterPanel, BorderLayout.NORTH);
    leftPanel.add(ScrollPaneFactory.createScrollPane(myThreadList, SideBorder.LEFT | SideBorder.RIGHT), BorderLayout.CENTER);

    final Splitter splitter = new Splitter(false, 0.3f);
    splitter.setFirstComponent(leftPanel);
    splitter.setSecondComponent(consoleView.getComponent());
    add(splitter, BorderLayout.CENTER);

    new ListSpeedSearch(myThreadList).setComparator(new SpeedSearchComparator(false, true));

    updateThreadList();

    final Editor editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext(consoleView.getPreferredFocusableComponent()));
    if (editor != null) {
      editor.getDocument().addDocumentListener(new com.intellij.openapi.editor.event.DocumentAdapter() {
        @Override
        public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
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
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.EXPORTER_TO_TEXT_FILE.is(dataId)) {
      return myExporterToTextFile;
    }
    return null;
  }

  private void updateThreadList() {
    String text = myFilterPanel.isVisible() ? myFilterField.getText() : "";
    Object selection = myThreadList.getSelectedValue();
    DefaultListModel model = (DefaultListModel)myThreadList.getModel();
    model.clear();
    int selectedIndex = 0;
    int index = 0;
    List<ThreadState> threadStates = UISettings.getInstance().MERGE_EQUAL_STACKTRACES ? myMergedThreadDump : myThreadDump;
    for (ThreadState state : threadStates) {
      if (StringUtil.containsIgnoreCase(state.getStackTrace(), text) || StringUtil.containsIgnoreCase(state.getName(), text)) {
        //noinspection unchecked
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
    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorManager = EditorColorsManager.getInstance();
    final TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES);
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

  private static Icon getThreadStateIcon(final ThreadState threadState) {
    final boolean daemon = threadState.isDaemon();
    if (threadState.isSleeping()) {
      return daemon ? PAUSE_ICON_DAEMON : Paused;
    }
    if (threadState.isWaiting()) {
      return daemon ? LOCKED_ICON_DAEMON : Locked;
    }
    if (threadState.getOperation() == ThreadOperation.Socket) {
      return daemon ? SOCKET_ICON_DAEMON : Socket;
    }
    if (threadState.getOperation() == ThreadOperation.IO) {
      return daemon ? IO_ICON_DAEMON : IO;
    }
    if (threadState.isEDT()) {
      if ("idle".equals(threadState.getThreadStateDetail())) {
        return daemon ? IDLE_ICON_DAEMON : Idle;
      }
      return daemon ? EDT_BUSY_ICON_DAEMON : EdtBusy;
    }
    return daemon ? RUNNING_ICON_DAEMON : Running;
  }

  private enum StateCode {RUN, RUN_IO, RUN_SOCKET, PAUSED, LOCKED, EDT, IDLE}
  private static StateCode getThreadStateCode(final ThreadState state) {
    if (state.isSleeping()) return StateCode.PAUSED;
    if (state.isWaiting()) return StateCode.LOCKED;
    if (state.getOperation() == ThreadOperation.Socket) return StateCode.RUN_SOCKET;
    if (state.getOperation() == ThreadOperation.IO) return StateCode.RUN_IO;
    if (state.isEDT()) {
      return "idle".equals(state.getThreadStateDetail()) ? StateCode.IDLE : StateCode.EDT;
    }
    return StateCode.RUN;
  }

  private static SimpleTextAttributes getAttributes(final ThreadState threadState) {
    if (threadState.isSleeping()) {
      return SimpleTextAttributes.GRAY_ATTRIBUTES;
    }
    if (threadState.isEmptyStackTrace() || ThreadDumpParser.isKnownJdkThread(threadState)) {
      return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.GRAY.brighter());
    }
    if (threadState.isEDT()) {
      return SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  private static class ThreadListCellRenderer extends ColoredListCellRenderer {

    @Override
    protected void customizeCellRenderer(@NotNull final JList list, final Object value, final int index, final boolean selected, final boolean hasFocus) {
      ThreadState threadState = (ThreadState) value;
      setIcon(getThreadStateIcon(threadState));
      if (!selected) {
        ThreadState selectedThread = (ThreadState)list.getSelectedValue();
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

  private class SortThreadsAction extends DumbAwareAction {
    private final Comparator<ThreadState> BY_TYPE = (o1, o2) -> {
      final int s1 = getThreadStateCode(o1).ordinal();
      final int s2 = getThreadStateCode(o2).ordinal();
      if (s1 == s2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      } else {
        return s1 < s2 ? - 1 :  1;
      }
    };

    private final Comparator<ThreadState> BY_NAME = (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName());
    private Comparator<ThreadState> COMPARATOR = BY_TYPE;
    private static final String TYPE_LABEL = "Sort threads by type";
    private static final String NAME_LABEL = "Sort threads by name";
    private SortThreadsAction() {
      super(TYPE_LABEL);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Collections.sort(myThreadDump, COMPARATOR);
      Collections.sort(myMergedThreadDump, COMPARATOR);
      updateThreadList();
      COMPARATOR = COMPARATOR == BY_TYPE ? BY_NAME : BY_TYPE;
      update(e);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setIcon(COMPARATOR == BY_TYPE ? AllIcons.ObjectBrowser.SortByType : AllIcons.ObjectBrowser.Sorted);
      e.getPresentation().setText(COMPARATOR == BY_TYPE ? TYPE_LABEL : NAME_LABEL);
    }
  }
  private static class CopyToClipboardAction extends DumbAwareAction {
    private static final NotificationGroup GROUP = NotificationGroup.toolWindowGroup("Analyze thread dump", ToolWindowId.RUN, false);
    private final List<ThreadState> myThreadDump;
    private final Project myProject;

    private CopyToClipboardAction(List<ThreadState> threadDump, Project project) {
      super("Copy to Clipboard", "Copy whole thread dump to clipboard", PlatformIcons.COPY_ICON);
      myThreadDump = threadDump;
      myProject = project;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final StringBuilder buf = new StringBuilder();
      buf.append("Full thread dump").append("\n\n");
      for (ThreadState state : myThreadDump) {
        buf.append(state.getStackTrace()).append("\n\n");
      }
      CopyPasteManager.getInstance().setContents(new StringSelection(buf.toString()));

      GROUP.createNotification("Full thread dump was successfully copied to clipboard", MessageType.INFO).notify(myProject);
    }
  }

  private class FilterAction extends ToggleAction implements DumbAware {

    private FilterAction() {
      super("Filter", "Show only threads containing a specific string", AllIcons.General.Filter);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myFilterPanel.isVisible();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myFilterPanel.setVisible(state);
      if (state) {
        IdeFocusManager.getInstance(getEventProject(e)).requestFocus(myFilterField, true);
        myFilterField.selectText();
      }
      updateThreadList();
    }
  }

  private class MergeStacktracesAction extends ToggleAction implements DumbAware {
    private MergeStacktracesAction() {
      super("Merge Identical Stacktraces", "Group threads with identical stacktraces", AllIcons.General.CollapseAll);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return UISettings.getInstance().MERGE_EQUAL_STACKTRACES;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      UISettings.getInstance().MERGE_EQUAL_STACKTRACES = state;
      updateThreadList();
    }
  }

  public static ExporterToTextFile createToFileExporter(Project project, List<ThreadState> threadStates) {
    return new MyToFileExporter(project, threadStates);
  }

  private static class MyToFileExporter implements ExporterToTextFile {
    private final Project myProject;
    private final List<ThreadState> myThreadStates;

    private MyToFileExporter(Project project, List<ThreadState> threadStates) {
      myProject = project;
      myThreadStates = threadStates;
    }

    @Override
    public JComponent getSettingsEditor() {
      return null;
    }

    @Override
    public void addSettingsChangedListener(ChangeListener listener) throws TooManyListenersException {}

    @Override
    public void removeSettingsChangedListener(ChangeListener listener) {}

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
      final VirtualFile baseDir = myProject.getBaseDir();
      if (baseDir != null) {
        return baseDir.getPresentableUrl() + File.separator + DEFAULT_REPORT_FILE_NAME;
      }
      return "";
    }

    @Override
    public void exportedTo(String filePath) {

    }

    @Override
    public boolean canExport() {
      return !myThreadStates.isEmpty();
    }
  }
}
