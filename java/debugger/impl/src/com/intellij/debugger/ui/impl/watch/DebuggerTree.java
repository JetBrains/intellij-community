// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class DebuggerTree
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.debugger.ui.impl.DebuggerTreeRenderer;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.tree.TreeUtil;
import com.sun.jdi.ThreadReference;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

public abstract class DebuggerTree extends DnDAwareTree implements UiDataProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(DebuggerTree.class);
  protected static final Key<Rectangle> VISIBLE_RECT = Key.create("VISIBLE_RECT");

  public static final DataKey<DebuggerTree> DATA_KEY = DataKey.create("DebuggerTree");

  protected final NodeManagerImpl myNodeManager;

  private DebuggerContextImpl myDebuggerContext = DebuggerContextImpl.EMPTY_CONTEXT;

  private DebuggerTreeNodeImpl myEditedNode;
  private final Project myProject;

  public DebuggerTree(Project project) {
    super((TreeModel)null);

    myProject = project;
    setRootVisible(false);
    setShowsRootHandles(true);
    setCellRenderer(new DebuggerTreeRenderer());
    updateUI();
    TreeUtil.installActions(this);

    setScrollsOnExpand(false);
    myNodeManager = createNodeManager(project);

    final TreeBuilder model = new TreeBuilder(this) {
      @Override
      public void buildChildren(TreeBuilderNode node) {
        final DebuggerTreeNodeImpl debuggerTreeNode = (DebuggerTreeNodeImpl)node;
        if (debuggerTreeNode.getDescriptor() instanceof DefaultNodeDescriptor) {
          return;
        }
        buildNode(debuggerTreeNode);
      }

      @Override
      public boolean isExpandable(TreeBuilderNode builderNode) {
        return DebuggerTree.this.isExpandable((DebuggerTreeNodeImpl)builderNode);
      }
    };
    model.setRoot(getNodeFactory().getDefaultNode());

    setModel(model);

    final TreeSpeedSearch search = TreeSpeedSearch.installOn(this);
    search.setComparator(new SpeedSearchComparator(false));
  }

  protected NodeManagerImpl createNodeManager(Project project) {
    return new NodeManagerImpl(project, this);
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  public void dispose() {
    myNodeManager.dispose();
    myDebuggerContext = DebuggerContextImpl.EMPTY_CONTEXT;
  }

  protected boolean isExpandable(DebuggerTreeNodeImpl node) {
    NodeDescriptorImpl descriptor = node.getDescriptor();
    return descriptor.isExpandable();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(DATA_KEY, this);
  }

  private void buildNode(final DebuggerTreeNodeImpl node) {
    if (node == null || node.getDescriptor() == null) {
      return;
    }
    DebuggerManagerThreadImpl managerThread = getDebuggerContext().getManagerThread();
    if (managerThread != null) {
      DebuggerCommandImpl command = getBuildNodeCommand(node);
      if (command != null) {
        node.add(myNodeManager.createMessageNode(MessageDescriptor.EVALUATING));
        managerThread.schedule(command);
      }
    }
  }

  // todo: convert "if" into instance method call
  protected DebuggerCommandImpl getBuildNodeCommand(final DebuggerTreeNodeImpl node) {
    if (node.getDescriptor() instanceof ThreadDescriptorImpl) {
      return new BuildThreadCommand(node);
    }
    else if (node.getDescriptor() instanceof ThreadGroupDescriptorImpl) {
      return new BuildThreadGroupCommand(node);
    }
    LOG.assertTrue(false);
    return null;
  }

  public void saveState(DebuggerTreeNodeImpl node) {
    if (node.getDescriptor() != null) {
      TreePath path = new TreePath(node.getPath());
      node.getDescriptor().myIsExpanded = isExpanded(path);
      node.getDescriptor().myIsSelected = getSelectionModel().isPathSelected(path);
      Rectangle rowBounds = getRowBounds(getRowForPath(path));
      if (rowBounds != null && getVisibleRect().contains(rowBounds)) {
        node.getDescriptor().putUserData(VISIBLE_RECT, getVisibleRect());
        node.getDescriptor().myIsVisible = true;
      }
      else {
        node.getDescriptor().putUserData(VISIBLE_RECT, null);
        node.getDescriptor().myIsVisible = false;
      }
    }

    for (Enumeration e = node.rawChildren(); e.hasMoreElements(); ) {
      DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)e.nextElement();
      saveState(child);
    }
  }

  public void restoreState(DebuggerTreeNodeImpl node) {
    restoreStateImpl(node);
    scrollToVisible(node);
  }

  protected final void scrollToVisible(DebuggerTreeNodeImpl scopeNode) {
    final TreePath rootPath = new TreePath(scopeNode.getPath());
    final int rowCount = getRowCount();
    for (int idx = rowCount - 1; idx >= 0; idx--) {
      final TreePath treePath = getPathForRow(idx);
      if (treePath != null) {
        if (!rootPath.isDescendant(treePath)) {
          continue;
        }
        final DebuggerTreeNodeImpl pathNode = (DebuggerTreeNodeImpl)treePath.getLastPathComponent();
        final NodeDescriptorImpl descriptor = pathNode.getDescriptor();

        if (descriptor != null && descriptor.myIsVisible) {
          final Rectangle visibleRect = descriptor.getUserData(VISIBLE_RECT);
          if (visibleRect != null) {
            // prefer visible rect
            scrollRectToVisible(visibleRect);
          }
          else {
            scrollPathToVisible(treePath);
          }
          break;
        }
      }
    }
  }

  @Override
  public void scrollRectToVisible(Rectangle aRect) {
    // see IDEADEV-432
    aRect.width += aRect.x;
    aRect.x = 0;
    super.scrollRectToVisible(aRect);
  }

  private void restoreStateImpl(DebuggerTreeNodeImpl node) {
    restoreNodeState(node);
    if (node.getDescriptor().myIsExpanded) {
      for (Enumeration e = node.rawChildren(); e.hasMoreElements(); ) {
        DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)e.nextElement();
        restoreStateImpl(child);
      }
    }
  }

  public void restoreState() {
    clearSelection();
    DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getModel().getRoot();
    if (root != null) {
      restoreState(root);
    }
  }

  protected void restoreNodeState(DebuggerTreeNodeImpl node) {
    final NodeDescriptorImpl descriptor = node.getDescriptor();
    if (descriptor != null) {
      if (node.getParent() == null) {
        descriptor.myIsExpanded = true;
      }

      TreePath path = new TreePath(node.getPath());
      if (descriptor.myIsExpanded) {
        expandPath(path);
      }
      if (descriptor.myIsSelected) {
        addSelectionPath(path);
      }
    }
  }

  public NodeManagerImpl getNodeFactory() {
    return myNodeManager;
  }

  public TreeBuilder getMutableModel() {
    return (TreeBuilder)getModel();
  }

  public void removeAllChildren() {
    DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getModel().getRoot();
    root.removeAllChildren();
    treeChanged();
  }

  public void showMessage(MessageDescriptor messageDesc) {
    DebuggerTreeNodeImpl root = getNodeFactory().getDefaultNode();
    getMutableModel().setRoot(root);
    DebuggerTreeNodeImpl message = root.add(messageDesc);
    treeChanged();
    expandPath(new TreePath(message.getPath()));
  }

  public void showMessage(@NlsContexts.Label String messageText) {
    showMessage(new MessageDescriptor(messageText));
  }

  public final void treeChanged() {
    DebuggerTreeNodeImpl node = (DebuggerTreeNodeImpl)getModel().getRoot();
    if (node != null) {
      getMutableModel().nodeStructureChanged(node);
      restoreState();
    }
  }

  protected abstract void build(DebuggerContextImpl context);

  public void rebuild(final DebuggerContextImpl context) {
    ThreadingAssertions.assertEventDispatchThread();
    DebuggerManagerThreadImpl managerThread = context.getManagerThread();
    if (managerThread == null) {
      return; // empty context, no process available yet
    }
    myDebuggerContext = context;
    saveState();
    managerThread.schedule(PrioritizedTask.Priority.NORMAL, () -> getNodeFactory().setHistoryByContext(context));
    build(context);
  }

  public void saveState() {
    saveState((DebuggerTreeNodeImpl)getModel().getRoot());
  }

  public void onEditorShown(DebuggerTreeNodeImpl node) {
    myEditedNode = node;
  }

  public void onEditorHidden(DebuggerTreeNodeImpl node) {
    if (myEditedNode != null) {
      assert myEditedNode == node;
      myEditedNode = null;
    }
  }

  public DebuggerContextImpl getDebuggerContext() {
    return myDebuggerContext;
  }

  public abstract class BuildNodeCommand extends DebuggerContextCommandImpl {
    protected final DebuggerTreeNodeImpl myNode;

    protected final List<DebuggerTreeNodeImpl> myChildren = new LinkedList<>();

    protected BuildNodeCommand(DebuggerTreeNodeImpl node, ThreadReferenceProxyImpl thread) {
      super(DebuggerTree.this.getDebuggerContext(), thread);
      myNode = node;
    }

    @Override
    public Priority getPriority() {
      return Priority.NORMAL;
    }

    protected void updateUI(final boolean scrollToVisible) {
      DebuggerInvocationUtil.swingInvokeLater(getProject(), () -> {
        myNode.removeAllChildren();
        for (DebuggerTreeNodeImpl debuggerTreeNode : myChildren) {
          myNode.add(debuggerTreeNode);
        }
        myNode.childrenChanged(scrollToVisible);
      });
    }
  }

  private class BuildThreadCommand extends BuildNodeCommand {
    BuildThreadCommand(DebuggerTreeNodeImpl threadNode) {
      super(threadNode, ((ThreadDescriptorImpl)threadNode.getDescriptor()).getThreadReference());
    }

    @Override
    public void threadAction(@NotNull SuspendContextImpl suspendContext) {
      ThreadDescriptorImpl threadDescriptor = ((ThreadDescriptorImpl)myNode.getDescriptor());
      ThreadReferenceProxyImpl threadProxy = threadDescriptor.getThreadReference();
      if (!threadProxy.isCollected() && getDebuggerContext().getDebugProcess().getSuspendManager().isSuspended(threadProxy)) {
        int status = threadProxy.status();
        if (!(status == ThreadReference.THREAD_STATUS_UNKNOWN) &&
            !(status == ThreadReference.THREAD_STATUS_NOT_STARTED) &&
            !(status == ThreadReference.THREAD_STATUS_ZOMBIE)) {
          try {
            for (StackFrameProxyImpl stackFrame : threadProxy.frames()) {
              //Method method = stackFrame.location().method();
              //ToDo :check whether is synthetic if (shouldDisplay(method)) {
              myChildren.add(myNodeManager.createNode(myNodeManager.getStackFrameDescriptor(threadDescriptor, stackFrame),
                                                      getDebuggerContext().createEvaluationContext()));
            }
          }
          catch (EvaluateException e) {
            myChildren.clear();
            myChildren.add(myNodeManager.createMessageNode(e.getMessage()));
            LOG.debug(e);
            //LOG.assertTrue(false);
            // if we pause during evaluation of this method the exception is thrown
            //  private static void longMethod() {
            //    try {
            //      Thread.sleep(100000);
            //    } catch (InterruptedException e) {
            //      e.printStackTrace();
            //    }
            //  }
          }
        }
      }
      updateUI(true);
    }
  }

  private class BuildThreadGroupCommand extends DebuggerCommandImpl {
    private final DebuggerTreeNodeImpl myNode;
    protected final List<DebuggerTreeNodeImpl> myChildren = new LinkedList<>();

    BuildThreadGroupCommand(DebuggerTreeNodeImpl node) {
      myNode = node;
    }

    @Override
    protected void action() {
      ThreadGroupDescriptorImpl groupDescriptor = (ThreadGroupDescriptorImpl)myNode.getDescriptor();
      ThreadGroupReferenceProxyImpl threadGroup = groupDescriptor.getThreadGroupReference();

      List<ThreadReferenceProxyImpl> threads = new ArrayList<>(threadGroup.threads());
      threads.sort(ThreadReferenceProxyImpl.ourComparator);

      final DebuggerContextImpl debuggerContext = getDebuggerContext();
      final SuspendContextImpl suspendContext = debuggerContext.getSuspendContext();
      final EvaluationContextImpl evaluationContext = suspendContext != null && !suspendContext.isResumed() ? debuggerContext.createEvaluationContext() : null;

      boolean showCurrent = ThreadsViewSettings.getInstance().SHOW_CURRENT_THREAD;

      for (final ThreadGroupReferenceProxyImpl group : threadGroup.threadGroups()) {
        if (group != null) {
          DebuggerTreeNodeImpl threadNode =
            myNodeManager.createNode(myNodeManager.getThreadGroupDescriptor(groupDescriptor, group), evaluationContext);

          if (showCurrent && ((ThreadGroupDescriptorImpl)threadNode.getDescriptor()).isCurrent()) {
            myChildren.add(0, threadNode);
          }
          else {
            myChildren.add(threadNode);
          }
        }
      }

      ArrayList<DebuggerTreeNodeImpl> threadNodes = new ArrayList<>();

      for (ThreadReferenceProxyImpl thread : threads) {
        if (thread != null) {
          final DebuggerTreeNodeImpl threadNode = myNodeManager.createNode(myNodeManager.getThreadDescriptor(groupDescriptor, thread), evaluationContext);
          if (showCurrent && ((ThreadDescriptorImpl)threadNode.getDescriptor()).isCurrent()) {
            threadNodes.add(0, threadNode);
          }
          else {
            threadNodes.add(threadNode);
          }
        }
      }

      myChildren.addAll(threadNodes);

      updateUI(true);
    }

    protected void updateUI(final boolean scrollToVisible) {
      DebuggerInvocationUtil.swingInvokeLater(getProject(), () -> {
        myNode.removeAllChildren();
        for (DebuggerTreeNodeImpl debuggerTreeNode : myChildren) {
          myNode.add(debuggerTreeNode);
        }
        myNode.childrenChanged(scrollToVisible);
      });
    }
  }
}
