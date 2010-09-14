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
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextUtil;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.DebuggerComboBoxRenderer;
import com.intellij.debugger.ui.impl.FramesList;
import com.intellij.debugger.ui.impl.UpdatableDebuggerView;
import com.intellij.debugger.ui.impl.watch.MethodsTracker;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.sun.jdi.ObjectCollectedException;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FramesPanel extends UpdatableDebuggerView {
  private final JComboBox myThreadsCombo;
  private final FramesList myFramesList;
  private final ThreadsListener myThreadsListener;
  private final FramesListener myFramesListener;
  private final DebuggerStateManager myStateManager;

  public FramesPanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);
    myStateManager = stateManager;

    setLayout(new BorderLayout());

    myThreadsCombo = new ComboBoxWithWidePopup();
    myThreadsCombo.setRenderer(new DebuggerComboBoxRenderer());
    myThreadsListener = new ThreadsListener();
    myThreadsCombo.addItemListener(myThreadsListener);

    myFramesList = new FramesList(project);
    myFramesListener = new FramesListener();
    myFramesList.addListSelectionListener(myFramesListener);

    myFramesList.addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        int index = myFramesList.locationToIndex(e.getPoint());
        if (index >= 0 && myFramesList.isSelectedIndex(index)) {
          processListValue(myFramesList.getModel().getElementAt(index));
        }
      }
    });

    registerThreadsPopupMenu(myFramesList);

    setBorder(null);
    add(myThreadsCombo, BorderLayout.NORTH);
    add(ScrollPaneFactory.createScrollPane(myFramesList), BorderLayout.CENTER);
  }

  public DebuggerStateManager getContextManager() {
    return myStateManager;
  }

  private class FramesListener implements ListSelectionListener {
    boolean myIsEnabled = true;

    public void setEnabled(boolean enabled) {
      myIsEnabled = enabled;
    }

    public void valueChanged(ListSelectionEvent e) {
      if (!myIsEnabled || e.getValueIsAdjusting()) {
        return;
      }
      final JList list = (JList)e.getSource();
      processListValue(list.getSelectedValue());
    }

  }
  private void processListValue(final Object selected) {
    if (selected instanceof StackFrameDescriptorImpl) {
      DebuggerContextUtil.setStackFrame(getContextManager(), ((StackFrameDescriptorImpl)selected).getFrameProxy());
    }
  }


  private void registerThreadsPopupMenu(final JList framesList) {
    final PopupHandler popupHandler = new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction(DebuggerActions.THREADS_PANEL_POPUP);
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(DebuggerActions.THREADS_PANEL_POPUP, group);
        popupMenu.getComponent().show(comp, x, y);
      }
    };
    framesList.addMouseListener(popupHandler);
    registerDisposable(new Disposable() {
      public void dispose() {
        myThreadsCombo.removeItemListener(myThreadsListener);
        framesList.removeMouseListener(popupHandler);
      }
    });
  }

  private class ThreadsListener implements ItemListener {
    boolean myIsEnabled = true;

    public void setEnabled(boolean enabled) {
      myIsEnabled = enabled;
    }

    public void itemStateChanged(ItemEvent e) {
      if (!myIsEnabled) return;
      if (e.getStateChange() == ItemEvent.SELECTED) {
        ThreadDescriptorImpl item = (ThreadDescriptorImpl)e.getItem();
        DebuggerContextUtil.setThread(getContextManager(), item);
      }
    }
  }

  /*invoked in swing thread*/
  protected void rebuild(int event) {
    final DebuggerContextImpl context = getContext();
    final boolean paused = context.getDebuggerSession().isPaused();
    final boolean isRefresh = event == DebuggerSession.EVENT_REFRESH ||
                              event == DebuggerSession.EVENT_REFRESH_VIEWS_ONLY ||
                              event == DebuggerSession.EVENT_THREADS_REFRESH;
    if (!paused || !isRefresh) {
      myThreadsCombo.removeAllItems();
      synchronized (myFramesList) {
        myFramesList.clear();
      }
    }

    if (paused) {
      final DebugProcessImpl process = context.getDebugProcess();
      if (process != null) {
        process.getManagerThread().schedule(new RefreshFramePanelCommand(isRefresh && myThreadsCombo.getItemCount() != 0));
      }
    }
  }

  private class RefreshFramePanelCommand extends DebuggerContextCommandImpl {
    private final boolean myRefreshOnly;
    private final ThreadDescriptorImpl[] myThreadDescriptorsToUpdate;

    public RefreshFramePanelCommand(final boolean refreshOnly) {
      super(getContext());
      myRefreshOnly = refreshOnly;
      if (refreshOnly) {
        final int size = myThreadsCombo.getItemCount();
        myThreadDescriptorsToUpdate = new ThreadDescriptorImpl[size];
        for (int idx = 0; idx < size; idx++) {
          myThreadDescriptorsToUpdate[idx] = (ThreadDescriptorImpl)myThreadsCombo.getItemAt(idx);
        }
      }
      else {
        myThreadDescriptorsToUpdate = null;
      }
    }

    private List<ThreadDescriptorImpl> createThreadDescriptorsList() {
      final List<ThreadReferenceProxyImpl> threads = new ArrayList<ThreadReferenceProxyImpl>(getSuspendContext().getDebugProcess().getVirtualMachineProxy().allThreads());
      Collections.sort(threads, ThreadReferenceProxyImpl.ourComparator);

      final List<ThreadDescriptorImpl> descriptors = new ArrayList<ThreadDescriptorImpl>(threads.size());
      EvaluationContextImpl evaluationContext = getDebuggerContext().createEvaluationContext();

      for (ThreadReferenceProxyImpl thread : threads) {
        ThreadDescriptorImpl threadDescriptor = new ThreadDescriptorImpl(thread);
        threadDescriptor.setContext(evaluationContext);
        threadDescriptor.updateRepresentation(evaluationContext, DescriptorLabelListener.DUMMY_LISTENER);
        descriptors.add(threadDescriptor);
      }
      return descriptors;
    }

    public void threadAction() {
      if (myRefreshOnly && myThreadDescriptorsToUpdate.length != myThreadsCombo.getItemCount()) {
        // there is no sense in refreshing combobox if thread list has changed since creation of this command
        return;
      }
      
      final DebuggerContextImpl context = getDebuggerContext();

      final ThreadReferenceProxyImpl threadToSelect = context.getThreadProxy();
      if(threadToSelect == null) {
        return;
      }

      final SuspendContextImpl threadContext = SuspendManagerUtil.getSuspendContextForThread(context.getSuspendContext(), threadToSelect);
      final ThreadDescriptorImpl currentThreadDescriptor = (ThreadDescriptorImpl)myThreadsCombo.getSelectedItem();
      final ThreadReferenceProxyImpl currentThread = currentThreadDescriptor != null? currentThreadDescriptor.getThreadReference() : null;

      if (myRefreshOnly && threadToSelect.equals(currentThread)) {
        context.getDebugProcess().getManagerThread().schedule(new UpdateFramesListCommand(context, threadContext));
      }
      else {
        context.getDebugProcess().getManagerThread().schedule(new RebuildFramesListCommand(context, threadContext));
      }

      if (myRefreshOnly) {
        final EvaluationContextImpl evaluationContext = context.createEvaluationContext();
        for (ThreadDescriptorImpl descriptor : myThreadDescriptorsToUpdate) {
          descriptor.setContext(evaluationContext);
          descriptor.updateRepresentation(evaluationContext, DescriptorLabelListener.DUMMY_LISTENER);
        }
        DebuggerInvocationUtil.swingInvokeLater(getProject(), new Runnable() {
          public void run() {
            try {
              myThreadsListener.setEnabled(false);
              selectThread(threadToSelect);
              myFramesList.repaint();
            }
            finally {
              myThreadsListener.setEnabled(true);
            }
          }
        });
      }
      else { // full rebuild
        refillThreadsCombo(threadToSelect);
      }
    }

    protected void commandCancelled() {
      if (!DebuggerManagerThreadImpl.isManagerThread()) {
        return;
      }
      // context thread is not suspended
      final DebuggerContextImpl context = getDebuggerContext();

      final SuspendContextImpl suspendContext = context.getSuspendContext();
      if (suspendContext == null) {
        return;
      }
      final ThreadReferenceProxyImpl threadToSelect = context.getThreadProxy();
      if(threadToSelect == null) {
        return;
      }

      if (!suspendContext.isResumed()) {
        final SuspendContextImpl threadContext = SuspendManagerUtil.getSuspendContextForThread(suspendContext, threadToSelect);
        context.getDebugProcess().getManagerThread().schedule(new RebuildFramesListCommand(context, threadContext));
        refillThreadsCombo(threadToSelect);
      }
    }

    private void refillThreadsCombo(final ThreadReferenceProxyImpl threadToSelect) {
      final List<ThreadDescriptorImpl> threadItems = createThreadDescriptorsList();
      DebuggerInvocationUtil.swingInvokeLater(getProject(), new Runnable() {
        public void run() {
          try {
            myThreadsListener.setEnabled(false);

            myThreadsCombo.removeAllItems();
            for (final ThreadDescriptorImpl threadItem : threadItems) {
              myThreadsCombo.addItem(threadItem);
            }

            selectThread(threadToSelect);
          }
          finally {
            myThreadsListener.setEnabled(true);
          }
        }
      });
    }

  }

  private class UpdateFramesListCommand extends SuspendContextCommandImpl {
    private final DebuggerContextImpl myDebuggerContext;

    public UpdateFramesListCommand(DebuggerContextImpl debuggerContext, SuspendContextImpl suspendContext) {
      super(suspendContext);
      myDebuggerContext = debuggerContext;
    }

    public void contextAction() throws Exception {
      updateFrameList(myDebuggerContext.getThreadProxy());
      DebuggerInvocationUtil.swingInvokeLater(getProject(), new Runnable() {
        public void run() {
          try {
            myFramesListener.setEnabled(false);
            final StackFrameProxyImpl contextFrame = getDebuggerContext().getFrameProxy();
            if(contextFrame != null) {
              selectFrame(contextFrame);
            }
          }
          finally {
            myFramesListener.setEnabled(true);
          }
        }
      });

    }

    private void updateFrameList(ThreadReferenceProxyImpl thread) {
      try {
        if(!getSuspendContext().getDebugProcess().getSuspendManager().isSuspended(thread)) {
          return;
        }
      }
      catch (ObjectCollectedException e) {
        return;
      }
      
      final EvaluationContextImpl evaluationContext = getDebuggerContext().createEvaluationContext();
      final List<StackFrameDescriptorImpl> descriptors = new ArrayList<StackFrameDescriptorImpl>();

      synchronized (myFramesList) {
        final DefaultListModel model = myFramesList.getModel();
        final int size = model.getSize();
        for (int i = 0; i < size; i++) {
          final Object elem = model.getElementAt(i);
          if (elem instanceof StackFrameDescriptorImpl) {
            descriptors.add((StackFrameDescriptorImpl)elem);
          }
        }
      }

      for (StackFrameDescriptorImpl descriptor : descriptors) {
        descriptor.setContext(evaluationContext);
        descriptor.updateRepresentation(evaluationContext, DescriptorLabelListener.DUMMY_LISTENER);
      }
    }

    public DebuggerContextImpl getDebuggerContext() {
      return myDebuggerContext;
    }
  }

  private class RebuildFramesListCommand extends SuspendContextCommandImpl {
    private final DebuggerContextImpl myDebuggerContext;

    public RebuildFramesListCommand(DebuggerContextImpl debuggerContext, SuspendContextImpl suspendContext) {
      super(suspendContext);
      myDebuggerContext = debuggerContext;
    }

    public void contextAction() throws Exception {
      final ThreadReferenceProxyImpl thread = myDebuggerContext.getThreadProxy();
      try {
        if(!getSuspendContext().getDebugProcess().getSuspendManager().isSuspended(thread)) {
          DebuggerInvocationUtil.swingInvokeLater(getProject(), new Runnable() {
            public void run() {
              try {
                myFramesListener.setEnabled(false);
                synchronized (myFramesList) {
                  final DefaultListModel model = myFramesList.getModel();
                  model.clear();
                  model.addElement(new Object() {
                    public String toString() {
                      return DebuggerBundle.message("frame.panel.frames.not.available");
                    }
                  });
                  myFramesList.setSelectedIndex(0);
                }
              }
              finally {
                myFramesListener.setEnabled(true);
              }
            }
          });
          
          return;
        }
      }
      catch (ObjectCollectedException e) {
        return;
      }

      List<StackFrameProxyImpl> frames;
      try {
        frames = thread.frames();
      }
      catch (EvaluateException e) {
        frames = Collections.emptyList();
      }

      final StackFrameProxyImpl contextFrame = myDebuggerContext.getFrameProxy();
      final EvaluationContextImpl evaluationContext = myDebuggerContext.createEvaluationContext();
      final DebuggerManagerThreadImpl managerThread = myDebuggerContext.getDebugProcess().getManagerThread();
      final MethodsTracker tracker = new MethodsTracker();
      final int totalFramesCount = frames.size();
      int index = 0;
      final long timestamp = System.nanoTime();
      for (StackFrameProxyImpl stackFrameProxy : frames) {
        managerThread.schedule(
          new AppendFrameCommand(getSuspendContext(), stackFrameProxy, evaluationContext, tracker, index++, stackFrameProxy.equals(contextFrame), totalFramesCount, timestamp)
        );
      }
    }
  }

  private void selectThread(ThreadReferenceProxyImpl toSelect) {
    int count = myThreadsCombo.getItemCount();
    for (int idx = 0; idx < count; idx++) {
      ThreadDescriptorImpl item = (ThreadDescriptorImpl)myThreadsCombo.getItemAt(idx);
      if (toSelect.equals(item.getThreadReference())) {
        if (!item.equals(myThreadsCombo.getSelectedItem())) {
          myThreadsCombo.setSelectedIndex(idx);
        }
        return;
      }
    }
  }

  /*invoked in swing thread*/
  private void selectFrame(StackFrameProxy frame) {
    synchronized (myFramesList) {
      final int count = myFramesList.getElementCount();
      final Object selectedValue = myFramesList.getSelectedValue();
      final DefaultListModel model = myFramesList.getModel();
      for (int idx = 0; idx < count; idx++) {
        final Object elem = model.getElementAt(idx);
        if (elem instanceof StackFrameDescriptorImpl) {
          final StackFrameDescriptorImpl item = (StackFrameDescriptorImpl)elem;
          if (frame.equals(item.getFrameProxy())) {
            if (!item.equals(selectedValue)) {
              myFramesList.setSelectedIndex(idx);
            }
            return;
          }
        }
      }
    }
  }

  
  private volatile long myFramesLastUpdateTime = 0L;
  private class AppendFrameCommand extends SuspendContextCommandImpl {
    private final StackFrameProxyImpl myFrame;
    private final EvaluationContextImpl myEvaluationContext;
    private final MethodsTracker myTracker;
    private final int myIndexToInsert;
    private final boolean myIsContextFrame;
    private final int myTotalFramesCount;
    private final long myTimestamp;

    public AppendFrameCommand(SuspendContextImpl suspendContext, StackFrameProxyImpl frame, EvaluationContextImpl evaluationContext,
                              MethodsTracker tracker, int indexToInsert, final boolean isContextFrame, final int totalFramesCount,
                              final long timestamp) {
      super(suspendContext);
      myFrame = frame;
      myEvaluationContext = evaluationContext;
      myTracker = tracker;
      myIndexToInsert = indexToInsert;
      myIsContextFrame = isContextFrame;
      myTotalFramesCount = totalFramesCount;
      myTimestamp = timestamp;
    }

    public void contextAction() throws Exception {
      final StackFrameDescriptorImpl descriptor = new StackFrameDescriptorImpl(myFrame, myTracker);
      descriptor.setContext(myEvaluationContext);
      descriptor.updateRepresentation(myEvaluationContext, DescriptorLabelListener.DUMMY_LISTENER);
      final Project project = getProject();
      DebuggerInvocationUtil.swingInvokeLater(project, new Runnable() {
        public void run() {
          try {
            myFramesListener.setEnabled(false);
            synchronized (myFramesList) {
              final DefaultListModel model = myFramesList.getModel();
              if (model.isEmpty() || myFramesLastUpdateTime < myTimestamp) {
                myFramesLastUpdateTime = myTimestamp;
                model.clear();
                for (int idx = 0; idx < myTotalFramesCount; idx++) {
                  final String label = "<frame " + idx + ">";
                  model.addElement(new Object() {
                    public String toString() {
                      return label;
                    }
                  });
                }
              }
              if (myTimestamp != myFramesLastUpdateTime) {
                return;  // the command has expired
              }
              model.removeElementAt(myIndexToInsert); // remove placeholder
              model.insertElementAt(descriptor, myIndexToInsert);
              if (myIsContextFrame) {
                myFramesList.setSelectedIndex(myIndexToInsert);
              }
            }
          }
          finally {
            myFramesListener.setEnabled(true);
          }
        }
      });
    }
  }

  public void requestFocus() {
    myThreadsCombo.requestFocus();
  }

  public OccurenceNavigator getOccurenceNavigator() {
    return myFramesList;
  }

  public FramesList getFramesList() {
    return myFramesList;
  }
}
