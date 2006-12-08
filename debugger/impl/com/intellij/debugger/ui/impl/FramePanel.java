package com.intellij.debugger.ui.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.DebugProcessImpl;
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
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FramePanel extends DebuggerPanel implements DataProvider{
  private static final Icon VARIABLES_ICON = IconLoader.getIcon("/debugger/value.png");
  private final JComboBox myThreadsCombo;
  private final FramesList myFramesList;
  private final ThreadsListener myThreadsListener;
  private final FramesListener myFramesListener;

  @NonNls private static final String HELP_ID = "debugging.debugFrame";
  private ThreeComponentsSplitter mySplitter;
  private static final Icon UNFREEZE_ICON = IconLoader.getIcon("/actions/resumeThread.png");
  private static final Icon FREEZE_ICON = IconLoader.getIcon("/actions/freezeThread.png");

  public FramePanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);
    setBorder(null);

    myThreadsCombo = new JComboBox();
    myThreadsCombo.setRenderer(new DebuggerComboBoxRenderer());
    myThreadsListener = new ThreadsListener();
    myThreadsCombo.addItemListener(myThreadsListener);

    myFramesList = new FramesList();
    myFramesListener = new FramesListener();
    myFramesList.addListSelectionListener(myFramesListener);
    registerThreadsPopupMenu(myFramesList);

    final JPanel threadsPanel = new JPanel(new BorderLayout());
    threadsPanel.setBorder(null);
    threadsPanel.add(myThreadsCombo, BorderLayout.NORTH);
    threadsPanel.add(new JScrollPane(myFramesList), BorderLayout.CENTER);

    final FrameDebuggerTree frameTree = getFrameTree();

    mySplitter = new ThreeComponentsSplitter();
    mySplitter.setFirstComponent(threadsPanel);

    final JPanel treePanel = new JPanel(new BorderLayout());
    treePanel.add(new JScrollPane(frameTree), BorderLayout.CENTER);
    final JLabel title = new JLabel(DebuggerBundle.message("debugger.session.tab.variables.title"));
    title.setIcon(VARIABLES_ICON);
    treePanel.add(title, BorderLayout.NORTH);

    mySplitter.setInnerComponent(treePanel);
    mySplitter.setLastComponent(null);

    add(mySplitter, BorderLayout.CENTER);

    registerDisposable(DebuggerAction.installEditAction(frameTree, DebuggerActions.EDIT_NODE_SOURCE));

    final AnAction setValueAction  = ActionManager.getInstance().getAction(DebuggerActions.SET_VALUE);
    setValueAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), frameTree);
    registerDisposable(new Disposable() {
      public void dispose() {
        setValueAction.unregisterCustomShortcutSet(frameTree);
      }
    });
  }

  private JPanel createThreadComboPanel(JComboBox threadsCombo) {
    final JPanel panel = new JPanel(new BorderLayout());
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new FreezeThreadAction());
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.COMBO_PAGER, group, true);
    panel.add(threadsCombo, BorderLayout.CENTER);
    panel.add(toolbar.getComponent(), BorderLayout.EAST);
    return panel;
  }

  public void addNotify() {
    super.addNotify();
    final ThreeComponentsSplitter splitter = mySplitter;
    // need invokeLater to be sure the splitter has been assigned with and height
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        setFirstProportion(DebuggerSettings.getInstance().THREADS_FRAME_SPLITTER_PROPORTION);
        setLastProportion(DebuggerSettings.getInstance().FRAME_WATCHES_SPLITTER_PROPORTION);
      }
    });
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
        framesList.removeMouseListener(popupHandler);
      }
    });
  }

  protected void rebuild(final boolean updateOnly) {
    if (!updateOnly) {
      myThreadsCombo.removeAllItems();
      myFramesList.clear();
    }

    getContext().getDebugProcess().getManagerThread().invokeLater(new RefreshFramePanelCommand(updateOnly));

    super.rebuild(updateOnly);
  }

  protected DebuggerTree createTreeView() {
    return new FrameDebuggerTree(getProject());
  }

  protected void showMessage(MessageDescriptor messageDescriptor) {
    myThreadsCombo.removeAllItems();
    myFramesList.clear();
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
    final DebuggerSettings settings = DebuggerSettings.getInstance();
    settings.THREADS_FRAME_SPLITTER_PROPORTION = getFirstProportion();
    if (mySplitter.getLastComponent() != null) {
      settings.FRAME_WATCHES_SPLITTER_PROPORTION = getLastProportion();
    }
    myThreadsCombo.removeItemListener(myThreadsListener);
    super.dispose();
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
    final int count = myFramesList.getElementCount();
    final StackFrameDescriptorImpl selectedValue = (StackFrameDescriptorImpl)myFramesList.getSelectedValue();
    final DefaultListModel model = myFramesList.getModel();
    for (int idx = 0; idx < count; idx++) {
      StackFrameDescriptorImpl item = (StackFrameDescriptorImpl)model.getElementAt(idx);
      if (frame.equals(item.getStackFrame())) {
        if (!item.equals(selectedValue)) {
          myFramesList.setSelectedIndex(idx);
        }
        return;
      }
    }
  }

  private float getFirstProportion() {
    final float totalSize = mySplitter.getOrientation() ? mySplitter.getHeight() : mySplitter.getWidth();
    final float componentSize = mySplitter.getFirstSize();
    return componentSize / (totalSize - 2.0f * mySplitter.getDividerWidth());
  }

  private void setFirstProportion(float proportion) {
    final int totalSize = mySplitter.getOrientation() ? mySplitter.getHeight() : mySplitter.getWidth();
    mySplitter.setFirstSize((int)(proportion * (float)(totalSize - 2 * mySplitter.getDividerWidth())));
  }

  private float getLastProportion() {
    final float totalSize = mySplitter.getOrientation() ? mySplitter.getHeight() : mySplitter.getWidth();
    final float componentSize = mySplitter.getLastSize();
    return componentSize / (totalSize - 2.0f * mySplitter.getDividerWidth());
  }

  private  void setLastProportion(float proportion) {
    final int componentSize = mySplitter.getOrientation() ? mySplitter.getHeight() : mySplitter.getWidth();
    mySplitter.setLastSize((int)(proportion * (float)(componentSize - 2 * mySplitter.getDividerWidth())));
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

  // todo: rewrite to be list selection listener
  private class FramesListener implements ListSelectionListener {
    boolean myIsEnabled = true;

    public void setEnabled(boolean enabled) {
      myIsEnabled = enabled;
    }

    public void valueChanged(ListSelectionEvent e) {
      if (!myIsEnabled || e.getValueIsAdjusting()) {
        return;
      }
      final JList list = (JList) e.getSource();
      final StackFrameDescriptorImpl item = (StackFrameDescriptorImpl)list.getSelectedValue();
      if(item != null) {
        DebuggerContextUtil.setStackFrame(getContextManager(), item.getStackFrame());
      }
    }
  }

  public void setWatchPanel(WatchPanel watches) {
    mySplitter.setLastComponent(watches);
  }

  public FrameDebuggerTree getFrameTree() {
    return (FrameDebuggerTree) getTree();
  }

  private class RefreshFramePanelCommand extends DebuggerContextCommandImpl {
    private final boolean myRefreshOnly;

    public RefreshFramePanelCommand(final boolean refreshOnly) {
      super(getContext());
      myRefreshOnly = refreshOnly;
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
        final DefaultComboBoxModel model = (DefaultComboBoxModel)myThreadsCombo.getModel();
        final int size = model.getSize();
        final EvaluationContextImpl evaluationContext = context.createEvaluationContext();
        for (int idx = 0; idx < size; idx++) {
          final ThreadDescriptorImpl descriptor = (ThreadDescriptorImpl)model.getElementAt(idx);
          descriptor.setContext(evaluationContext);
          descriptor.updateRepresentation(evaluationContext, DescriptorLabelListener.DUMMY_LISTENER);
        }
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            try {
              myThreadsListener.setEnabled(false);
              selectThread(threadToSelect);
            }
            finally {
              myThreadsListener.setEnabled(true);
            }
          }
        });
      }
      else { // full rebuild
        final List<ThreadDescriptorImpl> threadItems = createThreadDescriptorsList();
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
      if(!getSuspendContext().getDebugProcess().getSuspendManager().isSuspended(thread)) {
        return;
      }
      EvaluationContextImpl evaluationContext = getDebuggerContext().createEvaluationContext();
      final DefaultListModel model = myFramesList.getModel();
      final int size = model.getSize();
      for (int i = 0; i < size; i++) {
        final StackFrameDescriptorImpl descriptor = (StackFrameDescriptorImpl)model.getElementAt(i);
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
      final List<StackFrameDescriptorImpl> frameItems = getFrameList(myDebuggerContext.getThreadProxy());
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          try {
            myFramesListener.setEnabled(false);
            myFramesList.clear();
            final DefaultListModel listModel = myFramesList.getModel();
            for (final StackFrameDescriptorImpl frameItem : frameItems) {
              listModel.addElement(frameItem);
            }
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

    private List<StackFrameDescriptorImpl> getFrameList(ThreadReferenceProxyImpl thread) {
      if(!getSuspendContext().getDebugProcess().getSuspendManager().isSuspended(thread)) {
        return Collections.emptyList();
      }

      List<StackFrameProxyImpl> frames;
      try {
        frames = thread.frames();
      }
      catch (EvaluateException e) {
        frames = new ArrayList<StackFrameProxyImpl>();
      }

      final List<StackFrameDescriptorImpl> frameItems = new ArrayList<StackFrameDescriptorImpl>(frames.size());

      EvaluationContextImpl evaluationContext = getDebuggerContext().createEvaluationContext();
      for (StackFrameProxyImpl stackFrameProxy : frames) {
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


  private class FreezeThreadAction extends AnAction {

    public FreezeThreadAction() {
      super("", "", UNFREEZE_ICON);
    }

    public void actionPerformed(final AnActionEvent e) {
      final ThreadDescriptorImpl thread = (ThreadDescriptorImpl)myThreadsCombo.getSelectedItem();
      if(thread != null && thread.isSuspended()) {
        final ThreadReferenceProxyImpl threadReferenceProxy = thread.getThreadReference();
        final DebuggerContextImpl debuggerContext = getContext();
        final DebugProcessImpl process = debuggerContext.getDebugProcess();
        process.getManagerThread().invokeLater(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
          public void contextAction() throws Exception {
            if (thread.isFrozen()) {
              process.createResumeThreadCommand(getSuspendContext(), threadReferenceProxy).run();
            }
            else {
              process.createFreezeThreadCommand(threadReferenceProxy).run();
            }
          }
        });
      }
    }

    public void update(final AnActionEvent e) {
      super.update(e);

      final ThreadDescriptorImpl thread = (ThreadDescriptorImpl)myThreadsCombo.getSelectedItem();
      final boolean isFrozen = thread != null && thread.isFrozen();
      final String text = isFrozen? DebuggerBundle.message("action.resume.thread.text.unfreeze") : ActionsBundle.message("action.Debugger.FreezeThread.text");
      final Icon icon = isFrozen? UNFREEZE_ICON : FREEZE_ICON;
      final boolean enabled = (thread != null && thread.isSuspended());

      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(enabled);
      presentation.setText(text);
      presentation.setIcon(icon);
    }
  }
}