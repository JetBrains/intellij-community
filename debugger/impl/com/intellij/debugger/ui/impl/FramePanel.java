package com.intellij.debugger.ui.impl;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
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
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.Disposable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import org.jetbrains.annotations.NonNls;

public class FramePanel extends DebuggerPanel implements DataProvider{
  private JComboBox myThreadsCombo;
  private JComboBox myFramesCombo;
  private ThreadsListener myThreadsListener;
  private FramesListener myFramesListener;
  @NonNls private static final String HELP_ID = "debugging.debugFrame";

  public FramePanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);
    myThreadsCombo = new JComboBox();
    myFramesCombo = new JComboBox();
    myThreadsCombo.setRenderer(new DebuggerComboBoxRenderer());
    myFramesCombo.setRenderer(new DebuggerComboBoxRenderer());
    myThreadsListener = new ThreadsListener();
    myFramesListener = new FramesListener();
    myThreadsCombo.addItemListener(myThreadsListener);
    myFramesCombo.addActionListener(myFramesListener);

    Splitter splitter = new Splitter();
    splitter.setPreferredSize(new Dimension(-1, 23));
    splitter.setFirstComponent(new ComboPager(myFramesCombo, this));
    splitter.setSecondComponent(myThreadsCombo);

    this.add(splitter, BorderLayout.NORTH);

    final FrameDebuggerTree frameTree = getFrameTree();
    add(new JScrollPane(frameTree), BorderLayout.CENTER);

    final Disposable disposable = DebuggerAction.installEditAction(frameTree, DebuggerActions.EDIT_NODE_SOURCE);
    registerDisposable(disposable);

    final AnAction setValueAction  = ActionManager.getInstance().getAction(DebuggerActions.SET_VALUE);
    setValueAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), frameTree);
    registerDisposable(new Disposable() {
      public void dispose() {
        setValueAction.unregisterCustomShortcutSet(frameTree);
      }
    });
  }

  protected void rebuild(int event) {
    myThreadsCombo.removeAllItems();
    myFramesCombo.removeAllItems();

    getContext().getDebugProcess().getManagerThread().invokeLater(new RefreshFramePanelCommand());

    super.rebuild(event);
  }

  protected DebuggerTree createTreeView() {
    return new FrameDebuggerTree(getProject());
  }

  protected void showMessage(MessageDescriptor messageDescriptor) {
    myThreadsCombo.removeAllItems();
    myFramesCombo.removeAllItems();
    super.showMessage(messageDescriptor);
  }

  protected ActionPopupMenu createPopupMenu() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.FRAME_PANEL_POPUP);
    return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.FRAME_PANEL_POPUP, group);
  }

  public Object getData(String dataId) {
    if (DataConstantsEx.HELP_ID.equals(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }

  public void dispose() {
    super.dispose();
    myThreadsCombo.removeItemListener(myThreadsListener);
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

  private void selectFrame(StackFrameProxy frame) {
    int count = myFramesCombo.getItemCount();
    for (int idx = 0; idx < count; idx++) {
      StackFrameDescriptorImpl item = (StackFrameDescriptorImpl)myFramesCombo.getItemAt(idx);
      if (frame.equals(item.getStackFrame())) {
        if (!item.equals(myFramesCombo.getSelectedItem())) {
          myFramesCombo.setSelectedIndex(idx);
        }
        return;
      }
    }
  }

  private class ThreadsListener implements ItemListener{
    boolean myIsEnabled = true;

    public void setEnabled(boolean enabled) {
      myIsEnabled = enabled;
    }

    public void itemStateChanged(ItemEvent e) {
      if(!myIsEnabled) return;
      if (e.getStateChange() == ItemEvent.SELECTED) {
        ThreadDescriptorImpl item = (ThreadDescriptorImpl)e.getItem();
        DebuggerContextUtil.setThread(getContextManager(), item);
      }
    }

  }

  private class FramesListener implements ActionListener{
    boolean myIsEnabled = true;

    public void setEnabled(boolean enabled) {
      myIsEnabled = enabled;
    }

    public void actionPerformed(ActionEvent e) {
      if(!myIsEnabled) return;
      JComboBox combo = (JComboBox) e.getSource();
      StackFrameDescriptorImpl item = (StackFrameDescriptorImpl)combo.getSelectedItem();

      if(item != null) {
        DebuggerContextUtil.setStackFrame(getContextManager(), item.getStackFrame());
      }
    }
  }

  public FrameDebuggerTree getFrameTree() {
    return (FrameDebuggerTree) getTree();
  }

  private class RefreshFramePanelCommand extends DebuggerContextCommandImpl {

    public RefreshFramePanelCommand() {
      super(getContext());
    }

    private java.util.List<ThreadDescriptorImpl> getThreadList() {
      final java.util.List<ThreadReferenceProxyImpl> threads = new ArrayList<ThreadReferenceProxyImpl>(getSuspendContext().getDebugProcess().getVirtualMachineProxy().allThreads());
      Collections.sort(threads, ThreadReferenceProxyImpl.ourComparator);

      final java.util.List<ThreadDescriptorImpl> descriptors = new ArrayList<ThreadDescriptorImpl>(threads.size());
      EvaluationContextImpl evaluationContext = getDebuggerContext().createEvaluationContext();

      for (Iterator<ThreadReferenceProxyImpl> iterator = threads.iterator(); iterator.hasNext();) {
        ThreadReferenceProxyImpl thread = iterator.next();

        ThreadDescriptorImpl threadDescriptor = new ThreadDescriptorImpl(thread);
        threadDescriptor.setContext(evaluationContext);
        threadDescriptor.updateRepresentation(evaluationContext, DescriptorLabelListener.DUMMY_LISTENER);
        descriptors.add(threadDescriptor);
      }
      return descriptors;
    }

    public void threadAction() {
      final ThreadReferenceProxyImpl threadToSelect = getDebuggerContext().getThreadProxy();
      if(threadToSelect == null) return;

      final java.util.List<ThreadDescriptorImpl>     threadItems = getThreadList();

      SuspendContextImpl threadContext = SuspendManagerUtil.getSuspendContextForThread(getDebuggerContext().getSuspendContext(), threadToSelect);
      getDebuggerContext().getDebugProcess().getManagerThread().invokeLater(new RefreshFramesListCommand(getDebuggerContext(), threadContext));

      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          try {
            myThreadsListener.setEnabled(false);

            myThreadsCombo.removeAllItems();
            for (Iterator<ThreadDescriptorImpl> iterator = threadItems.iterator(); iterator.hasNext();) {
              myThreadsCombo.addItem(iterator.next());
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

  private class RefreshFramesListCommand extends SuspendContextCommandImpl {
    private final DebuggerContextImpl myDebuggerContext;

    public RefreshFramesListCommand(DebuggerContextImpl debuggerContext, SuspendContextImpl suspendContext) {
      super(suspendContext);
      myDebuggerContext = debuggerContext;
    }

    public void contextAction() throws Exception {
      final java.util.List<StackFrameDescriptorImpl> frameItems  = getFrameList(myDebuggerContext.getThreadProxy());

      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          try {
            myFramesListener.setEnabled(false);
            myFramesCombo.removeAllItems();
            for (Iterator<StackFrameDescriptorImpl> iterator = frameItems.iterator(); iterator.hasNext();) {
              myFramesCombo.addItem(iterator.next());
            }
            if(getDebuggerContext().getFrameProxy() != null) {
              selectFrame(getDebuggerContext().getFrameProxy());
            }
          } finally {
            myFramesListener.setEnabled(true);
          }
        }
      });

    }

    private java.util.List<StackFrameDescriptorImpl> getFrameList(ThreadReferenceProxyImpl thread) {
      if(!getSuspendContext().getDebugProcess().getSuspendManager().isSuspended(thread)) {
        return Collections.<StackFrameDescriptorImpl>emptyList();
      }

      java.util.List<StackFrameProxyImpl> frames;
      try {
        frames = thread.frames();
      }
      catch (EvaluateException e) {
        frames = new ArrayList<StackFrameProxyImpl>();
      }

      final java.util.List<StackFrameDescriptorImpl> frameItems = new ArrayList<StackFrameDescriptorImpl>(frames.size());

      EvaluationContextImpl evaluationContext = getDebuggerContext().createEvaluationContext();
      for (Iterator<StackFrameProxyImpl> iterator = frames.iterator(); iterator.hasNext();) {
        StackFrameProxyImpl stackFrameProxy = iterator.next();
        StackFrameDescriptorImpl descriptor = new StackFrameDescriptorImpl(stackFrameProxy);
        descriptor.setContext(evaluationContext);
        descriptor.updateRepresentation(evaluationContext, DescriptorLabelListener.DUMMY_LISTENER);
        frameItems.add(descriptor);
      }

      return frameItems;
    }

    public DebuggerContextImpl getDebuggerContext() {
      return myDebuggerContext;
    }
  }

}