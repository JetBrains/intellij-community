package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.actions.DebuggerActions;
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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBComboBox;
import com.intellij.ui.PopupHandler;
import com.sun.jdi.ObjectCollectedException;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;

public class FramesPanel extends UpdatableDebuggerView {
  private final JComboBox myThreadsCombo;
  private final FramesList myFramesList;
  private final ThreadsListener myThreadsListener;
  private final FramesListener myFramesListener;
  private DebuggerStateManager myStateManager;

  public FramesPanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);
    myStateManager = stateManager;

    setLayout(new BorderLayout());

    myThreadsCombo = new JBComboBox();
    myThreadsCombo.setRenderer(new DebuggerComboBoxRenderer());
    myThreadsListener = new ThreadsListener();
    myThreadsCombo.addItemListener(myThreadsListener);

    myFramesList = new FramesList();
    myFramesListener = new FramesListener();
    myFramesList.addListSelectionListener(myFramesListener);
    registerThreadsPopupMenu(myFramesList);

    setBorder(null);
    add(myThreadsCombo, BorderLayout.NORTH);
    add(new JScrollPane(myFramesList), BorderLayout.CENTER);
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
      final Object selected = list.getSelectedValue();
      if (selected instanceof StackFrameDescriptorImpl) {
        DebuggerContextUtil.setStackFrame(getContextManager(), ((StackFrameDescriptorImpl)selected).getFrameProxy());
      }
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
  protected void rebuild(final boolean updateOnly) {
    if (!updateOnly) {
      myThreadsCombo.removeAllItems();
      synchronized (myFramesList) {
        myFramesList.clear();
      }
    }

    getContext().getDebugProcess().getManagerThread().invokeLater(new RefreshFramePanelCommand(updateOnly));
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

    private java.util.List<ThreadDescriptorImpl> createThreadDescriptorsList() {
      final java.util.List<ThreadReferenceProxyImpl> threads = new ArrayList<ThreadReferenceProxyImpl>(getSuspendContext().getDebugProcess().getVirtualMachineProxy().allThreads());
      Collections.sort(threads, ThreadReferenceProxyImpl.ourComparator);

      final java.util.List<ThreadDescriptorImpl> descriptors = new ArrayList<ThreadDescriptorImpl>(threads.size());
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
        context.getDebugProcess().getManagerThread().invokeLater(new UpdateFramesListCommand(context, threadContext));
      }
      else {
        context.getDebugProcess().getManagerThread().invokeLater(new RebuildFramesListCommand(context, threadContext));
      }

      if (myRefreshOnly) {
        final EvaluationContextImpl evaluationContext = context.createEvaluationContext();
        for (ThreadDescriptorImpl descriptor : myThreadDescriptorsToUpdate) {
          descriptor.setContext(evaluationContext);
          descriptor.updateRepresentation(evaluationContext, DescriptorLabelListener.DUMMY_LISTENER);
        }
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
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
        final java.util.List<ThreadDescriptorImpl> threadItems = createThreadDescriptorsList();
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
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
  }

  private class UpdateFramesListCommand extends SuspendContextCommandImpl {
    private final DebuggerContextImpl myDebuggerContext;

    public UpdateFramesListCommand(DebuggerContextImpl debuggerContext, SuspendContextImpl suspendContext) {
      super(suspendContext);
      myDebuggerContext = debuggerContext;
    }

    public void contextAction() throws Exception {
      updateFrameList(myDebuggerContext.getThreadProxy());
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
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
      final java.util.List<StackFrameDescriptorImpl> descriptors = new ArrayList<StackFrameDescriptorImpl>();

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
          return;
        }
      }
      catch (ObjectCollectedException e) {
        return;
      }

      java.util.List<StackFrameProxyImpl> frames;
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
      for (StackFrameProxyImpl stackFrameProxy : frames) {
        managerThread.invokeLater(
          new AppendFrameCommand(getSuspendContext(), stackFrameProxy, evaluationContext, tracker, index++, stackFrameProxy.equals(contextFrame), totalFramesCount)
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

  private class AppendFrameCommand extends SuspendContextCommandImpl {
    private final StackFrameProxyImpl myFrame;
    private final EvaluationContextImpl myEvaluationContext;
    private final MethodsTracker myTracker;
    private final int myIndexToInsert;
    private final boolean myIsContextFrame;
    private final int myTotalFramesCount;

    public AppendFrameCommand(SuspendContextImpl suspendContext, StackFrameProxyImpl frame, EvaluationContextImpl evaluationContext,
                              MethodsTracker tracker, int indexToInsert, final boolean isContextFrame, final int totalFramesCount) {
      super(suspendContext);
      myFrame = frame;
      myEvaluationContext = evaluationContext;
      myTracker = tracker;
      myIndexToInsert = indexToInsert;
      myIsContextFrame = isContextFrame;
      myTotalFramesCount = totalFramesCount;
    }

    public void contextAction() throws Exception {
      final StackFrameDescriptorImpl descriptor = new StackFrameDescriptorImpl(myFrame, myTracker);
      descriptor.setContext(myEvaluationContext);
      descriptor.updateRepresentation(myEvaluationContext, DescriptorLabelListener.DUMMY_LISTENER);
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          try {
            myFramesListener.setEnabled(false);
            synchronized (myFramesList) {
              final DefaultListModel model = myFramesList.getModel();
              if (model.size() == 0) {
                for (int idx = 0; idx < myTotalFramesCount; idx++) {
                  final String label = "<frame " + idx + ">";
                  model.addElement(new Object() {
                    public String toString() {
                      return label;
                    }
                  });
                }
              }
              model.remove(myIndexToInsert); // remove placeholder
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
}
