/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Jeka
 * @author Konstantin Bulenkov
 */
public class ThreadDumpPanel extends JPanel {
  private static final Icon DAEMON = IconLoader.getIcon("/debugger/threadStates/daemon_sign.png");
  private static final Icon PAUSE_ICON = IconLoader.getIcon("/debugger/threadStates/paused.png");
  private static final Icon PAUSE_ICON_DAEMON = new LayeredIcon(PAUSE_ICON, DAEMON);
  private static final Icon LOCKED_ICON = IconLoader.getIcon("/debugger/threadStates/locked.png");
  private static final Icon LOCKED_ICON_DAEMON = new LayeredIcon(LOCKED_ICON, DAEMON);
  private static final Icon RUNNING_ICON = IconLoader.getIcon("/debugger/threadStates/running.png");
  private static final Icon RUNNING_ICON_DAEMON = new LayeredIcon(RUNNING_ICON, DAEMON);
  private static final Icon SOCKET_ICON = IconLoader.getIcon("/debugger/threadStates/socket.png");
  private static final Icon SOCKET_ICON_DAEMON = new LayeredIcon(SOCKET_ICON, DAEMON);
  private static final Icon IDLE_ICON = IconLoader.getIcon("/debugger/threadStates/idle.png");
  private static final Icon IDLE_ICON_DAEMON = new LayeredIcon(IDLE_ICON, DAEMON);
  private static final Icon EDT_BUSY_ICON = IconLoader.getIcon("/debugger/threadStates/edtBusy.png");
  private static final Icon EDT_BUSY_ICON_DAEMON = new LayeredIcon(EDT_BUSY_ICON, DAEMON);
  private static final Icon IO_ICON = IconLoader.getIcon("/debugger/threadStates/io.png");
  private static final Icon IO_ICON_DAEMON = new LayeredIcon(IO_ICON, DAEMON);
  private final JBList myThreadList;

  public ThreadDumpPanel(Project project, final ConsoleView consoleView, final DefaultActionGroup toolbarActions, final List<ThreadState> threadDump) {
    super(new BorderLayout());
    final ThreadState[] data = threadDump.toArray(new ThreadState[threadDump.size()]);
    DefaultListModel model = new DefaultListModel();
    for (ThreadState threadState : data) {
      model.addElement(threadState);
    }
    myThreadList = new JBList(model);
    myThreadList.setCellRenderer(new ThreadListCellRenderer());
    myThreadList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myThreadList.addListSelectionListener(new ListSelectionListener() {
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
    toolbarActions.add(new CopyToClipboardAction(threadDump, project));
    toolbarActions.add(new SortThreadsAction());
    add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions,false).getComponent(), BorderLayout.WEST);

    final Splitter splitter = new Splitter(false, 0.3f);
    splitter.setFirstComponent(new JScrollPane(myThreadList));
    splitter.setSecondComponent(consoleView.getComponent());

    add(splitter, BorderLayout.CENTER);
  }

  private static Icon getThreadStateIcon(final ThreadState threadState) {
    final boolean daemon = threadState.isDaemon();
    if (threadState.isSleeping()) {
      return daemon ? PAUSE_ICON_DAEMON : PAUSE_ICON;
    }
    if (threadState.isWaiting()) {
      return daemon ? LOCKED_ICON_DAEMON : LOCKED_ICON;
    }
    if (threadState.getOperation() == ThreadOperation.Socket) {
      return daemon ? SOCKET_ICON_DAEMON : SOCKET_ICON;
    }
    if (threadState.getOperation() == ThreadOperation.IO) {
      return daemon ? IO_ICON_DAEMON : IO_ICON;
    }
    if (threadState.isEDT()) {
      if ("idle".equals(threadState.getThreadStateDetail())) {
        return daemon ? IDLE_ICON_DAEMON : IDLE_ICON;
      }
      return daemon ? EDT_BUSY_ICON_DAEMON : EDT_BUSY_ICON;
    }
    return daemon ? RUNNING_ICON_DAEMON : RUNNING_ICON;
  }

  private static enum StateCode {RUN, RUN_IO, RUN_SOCKET, PAUSED, LOCKED, EDT, IDLE}
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

    protected void customizeCellRenderer(final JList list, final Object value, final int index, final boolean selected, final boolean hasFocus) {
      ThreadState threadState = (ThreadState) value;
      setIcon(getThreadStateIcon(threadState));
      if (!selected) {
        ThreadState selectedThread = (ThreadState)list.getSelectedValue();
        if (threadState.isDeadlocked()) {
          setBackground(LightColors.RED);
        }
        else if (selectedThread != null && threadState.isAwaitedBy(selectedThread)) {
          setBackground(Color.YELLOW);
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

  private class SortThreadsAction extends AnAction {
    private final Comparator<ThreadState> BY_TYPE = new Comparator<ThreadState>() {
      public int compare(ThreadState o1, ThreadState o2) {
        final int s1 = getThreadStateCode(o1).ordinal();
        final int s2 = getThreadStateCode(o2).ordinal();
        if (s1 == s2) {
          return o1.getName().compareTo(o2.getName());
        } else {
          return s1 < s2 ? - 1 :  1;
        }
      }
    };

    private final Comparator<ThreadState> BY_NAME = new Comparator<ThreadState>() {
      public int compare(ThreadState o1, ThreadState o2) {
        return o1.getName().compareTo(o2.getName());
      }
    };
    private Comparator<ThreadState> COMPARATOR = BY_TYPE;
    private final Icon typeIcon = IconLoader.getIcon("/objectBrowser/sortByType.png");
    private final Icon nameIcon = IconLoader.getIcon("/icons/inspector/sortByName.png");
    private static final String TYPE_LABEL = "Sort threads by type";
    private static final String NAME_LABEL = "Sort threads by name";
    public SortThreadsAction() {
      super(TYPE_LABEL);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final DefaultListModel model = (DefaultListModel)myThreadList.getModel();
      final ThreadState selected = (ThreadState)myThreadList.getSelectedValue();
      ArrayList<ThreadState> states = new ArrayList<ThreadState>();
      for (int i = 0; i < model.getSize(); i++) {
        states.add((ThreadState)model.getElementAt(i));
      }
      Collections.sort(states, COMPARATOR);
      int selectedIndex = 0;
      for (int i = 0; i < states.size(); i++) {
        final ThreadState state = states.get(i);
        model.setElementAt(state, i);
        if (state == selected) {
          selectedIndex = i;
        }
      }
      myThreadList.setSelectedIndex(selectedIndex);
      COMPARATOR = COMPARATOR == BY_TYPE ? BY_NAME : BY_TYPE;
      update(e);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setIcon(COMPARATOR == BY_TYPE ? typeIcon : nameIcon);
      e.getPresentation().setText(COMPARATOR == BY_TYPE ? TYPE_LABEL : NAME_LABEL);
    }
  }
  private static class CopyToClipboardAction extends AnAction {
    private final List<ThreadState> myThreadDump;
    private final Project myProject;

    public CopyToClipboardAction(List<ThreadState> threadDump, Project project) {
      super("Copy to Clipboard", "Copy whole thread dump to clipboard", IconLoader.getIcon("/general/copy.png"));
      myThreadDump = threadDump;
      myProject = project;
    }

    public void actionPerformed(AnActionEvent e) {
      final StringBuilder buf = new StringBuilder();
      for (ThreadState state : myThreadDump) {
        buf.append(state.getStackTrace()).append("\n\n");
      }
      CopyPasteManager.getInstance().setContents(new StringSelection(buf.toString()));
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (!myProject.isDisposed()) {
            ToolWindowManager.getInstance(myProject).notifyByBalloon(ToolWindowId.RUN, MessageType.INFO, "Full thread dump was successfully copied to clipboard");
          }
        }
      });
    }
  }
}
