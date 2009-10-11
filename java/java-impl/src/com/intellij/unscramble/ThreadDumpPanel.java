/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.LightColors;
import com.intellij.ui.ListToolTipHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

public class ThreadDumpPanel extends JPanel {
  private static final Icon PAUSE_ICON = IconLoader.getIcon("/debugger/threadStates/paused.png");
  private static final Icon LOCKED_ICON = IconLoader.getIcon("/debugger/threadStates/locked.png");
  private static final Icon RUNNING_ICON = IconLoader.getIcon("/debugger/threadStates/running.png");
  private static final Icon SOCKET_ICON = IconLoader.getIcon("/debugger/threadStates/socket.png");
  private static final Icon IDLE_ICON = IconLoader.getIcon("/debugger/threadStates/idle.png");
  private static final Icon EDT_BUSY_ICON = IconLoader.getIcon("/debugger/threadStates/edtBusy.png");
  private static final Icon IO_ICON = IconLoader.getIcon("/debugger/threadStates/io.png");
  private JList myThreadList;

  public ThreadDumpPanel(Project project, final ConsoleView consoleView, final DefaultActionGroup toolbarActions, final List<ThreadState> threadDump) {
    super(new BorderLayout());

    myThreadList = new JList(threadDump.toArray(new ThreadState[threadDump.size()]));
    myThreadList.setCellRenderer(new ThreadListCellRenderer());
    myThreadList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myThreadList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        int index = myThreadList.getSelectedIndex();
        if (index >= 0) {
          ThreadState selection = threadDump.get(index);
          AnalyzeStacktraceUtil.printStacktrace(consoleView, selection.getStackTrace());
        }
        else {
          AnalyzeStacktraceUtil.printStacktrace(consoleView, "");
        }
        myThreadList.repaint();
      }
    });
    ListToolTipHandler.install(myThreadList);
    toolbarActions.add(new CopyToClipboardAction(threadDump, project));
    add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions,false).getComponent(), BorderLayout.WEST);

    final Splitter splitter = new Splitter(false, 0.3f);
    splitter.setFirstComponent(new JScrollPane(myThreadList));
    splitter.setSecondComponent(consoleView.getComponent());

    add(splitter, BorderLayout.CENTER);
  }

  private static Icon getThreadStateIcon(final ThreadState threadState) {
    if (threadState.isSleeping()) {
      return PAUSE_ICON;
    }
    if (threadState.isWaiting()) {
      return LOCKED_ICON;
    }
    if (threadState.getOperation() == ThreadOperation.Socket) {
      return SOCKET_ICON;
    }
    if (threadState.getOperation() == ThreadOperation.IO) {
      return IO_ICON;
    }
    if (threadState.isEDT()) {
      if ("idle".equals(threadState.getThreadStateDetail())) {
        return IDLE_ICON;
      }
      return EDT_BUSY_ICON;
    }
    return RUNNING_ICON;
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
