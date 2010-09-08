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
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.tree.TreeModelAdapter;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * User: lex
 * Date: Sep 26, 2003
 * Time: 5:57:58 PM
 */
public class ThreadsDebuggerTree extends DebuggerTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.ThreadsDebuggerTree");

  public ThreadsDebuggerTree(Project project) {
    super(project);
  }

  protected NodeManagerImpl createNodeManager(Project project) {
    return new NodeManagerImpl(project, this) {
      public String getContextKey(StackFrameProxyImpl frame) {
        return "ThreadsView";
      }
    };
  }

  protected boolean isExpandable(DebuggerTreeNodeImpl node) {
    NodeDescriptorImpl descriptor = node.getDescriptor();
    if(descriptor instanceof StackFrameDescriptorImpl) {
      return false;
    }
    return descriptor.isExpandable();
  }

  protected void build(DebuggerContextImpl context) {
    DebuggerSession debuggerSession = context.getDebuggerSession();
    final RefreshThreadsTreeCommand command = new RefreshThreadsTreeCommand(debuggerSession);
    
    final int state = debuggerSession.getState();
    if (ApplicationManager.getApplication().isUnitTestMode() || state == DebuggerSession.STATE_PAUSED || state == DebuggerSession.STATE_RUNNING) {
      showMessage(MessageDescriptor.EVALUATING);
      context.getDebugProcess().getManagerThread().schedule(command);
    }
    else {
      showMessage(debuggerSession.getStateDescription());
    }
  }

  private class RefreshThreadsTreeCommand extends DebuggerCommandImpl{
    private final DebuggerSession mySession;

    public RefreshThreadsTreeCommand(DebuggerSession session) {
      mySession = session;
    }

    protected void action() throws Exception {
      final DebuggerTreeNodeImpl root = getNodeFactory().getDefaultNode();

      final DebugProcessImpl debugProcess = mySession.getProcess();
      if(debugProcess == null || !debugProcess.isAttached()) {
        return;
      }
      final DebuggerContextImpl context = mySession.getContextManager().getContext();
      final SuspendContextImpl suspendContext = context.getSuspendContext();
      final ThreadReferenceProxyImpl suspendContextThread = suspendContext != null? suspendContext.getThread() : null;
      
      final boolean showGroups = ThreadsViewSettings.getInstance().SHOW_THREAD_GROUPS;
      try {
        final ThreadReferenceProxyImpl currentThread = ThreadsViewSettings.getInstance().SHOW_CURRENT_THREAD ? suspendContextThread : null;
        final VirtualMachineProxyImpl vm = debugProcess.getVirtualMachineProxy();

        final EvaluationContextImpl evaluationContext = suspendContext != null? getDebuggerContext().createEvaluationContext() : null;
        final NodeManagerImpl nodeManager = getNodeFactory();

        if (showGroups) {
          ThreadGroupReferenceProxyImpl topCurrentGroup = null;

          if (currentThread != null) {
            topCurrentGroup = currentThread.threadGroupProxy();
            if (topCurrentGroup != null) {
              for(ThreadGroupReferenceProxyImpl parentGroup = topCurrentGroup.parent(); parentGroup != null; parentGroup = parentGroup.parent()) {
                topCurrentGroup = parentGroup;
              }
            }

            if(topCurrentGroup != null){
              root.add(nodeManager.createNode(nodeManager.getThreadGroupDescriptor(null, topCurrentGroup), evaluationContext));
            }
            else {
              root.add(nodeManager.createNode(nodeManager.getThreadDescriptor(null, currentThread), evaluationContext));
            }
          }

          for (ThreadGroupReferenceProxyImpl group : vm.topLevelThreadGroups()) {
            if (group != topCurrentGroup) {
              DebuggerTreeNodeImpl threadGroup = nodeManager.createNode(nodeManager.getThreadGroupDescriptor(null, group), evaluationContext);
              root.add(threadGroup);
            }
          }
        }
        else {
          // do not show thread groups
          if (currentThread != null) {
            root.insert(nodeManager.createNode(nodeManager.getThreadDescriptor(null, currentThread), evaluationContext), 0);
          }
          List<ThreadReferenceProxyImpl> allThreads = new ArrayList<ThreadReferenceProxyImpl>(vm.allThreads());
          Collections.sort(allThreads, ThreadReferenceProxyImpl.ourComparator);

          for (ThreadReferenceProxyImpl threadProxy : allThreads) {
            if (threadProxy.equals(currentThread)) {
              continue;
            }
            root.add(nodeManager.createNode(nodeManager.getThreadDescriptor(null, threadProxy), evaluationContext));
          }
        }
      }
      catch (Exception ex) {
        root.add( MessageDescriptor.DEBUG_INFO_UNAVAILABLE);
        if (LOG.isDebugEnabled()) {
          LOG.debug(ex);
        }
      }

      final boolean hasThreadToSelect = suspendContextThread != null; // thread can be null if pause was pressed
      final List<ThreadGroupReferenceProxyImpl> groups;
      if (hasThreadToSelect && showGroups) {
        groups = new ArrayList<ThreadGroupReferenceProxyImpl>();
        for(ThreadGroupReferenceProxyImpl group = suspendContextThread.threadGroupProxy(); group != null; group = group.parent()) {
          groups.add(group);
        }
        Collections.reverse(groups);
      }
      else {
        groups = Collections.emptyList();
      }

      DebuggerInvocationUtil.swingInvokeLater(getProject(), new Runnable() {
        public void run() {
          getMutableModel().setRoot(root);
          treeChanged();
          if (hasThreadToSelect) {
            selectThread(groups, suspendContextThread, true);
          }
        }
      });
    }

    private void selectThread(final List<ThreadGroupReferenceProxyImpl> pathToThread, final ThreadReferenceProxyImpl thread, final boolean expand) {
      LOG.assertTrue(SwingUtilities.isEventDispatchThread());
      class MyTreeModelAdapter extends TreeModelAdapter {
        private void structureChanged(DebuggerTreeNodeImpl node) {
          for(Enumeration enumeration = node.children(); enumeration.hasMoreElements(); ) {
            DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)enumeration.nextElement();
            nodeChanged(child);
          }
        }

        private void nodeChanged(DebuggerTreeNodeImpl debuggerTreeNode) {
          if(pathToThread.size() == 0) {
            if(debuggerTreeNode.getDescriptor() instanceof ThreadDescriptorImpl && ((ThreadDescriptorImpl) debuggerTreeNode.getDescriptor()).getThreadReference() == thread) {
              removeListener();
              final TreePath treePath = new TreePath(debuggerTreeNode.getPath());
              setSelectionPath(treePath);
              if (expand && !isExpanded(treePath)) {
                expandPath(treePath);
              }
            }
          }
          else {
            if(debuggerTreeNode.getDescriptor() instanceof ThreadGroupDescriptorImpl && ((ThreadGroupDescriptorImpl) debuggerTreeNode.getDescriptor()).getThreadGroupReference() == pathToThread.get(0)) {
              pathToThread.remove(0);
              expandPath(new TreePath(debuggerTreeNode.getPath()));
            }
          }
        }

        private void removeListener() {
          final TreeModelAdapter listener = this;
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              getModel().removeTreeModelListener(listener);
            }
          });
        }

        public void treeStructureChanged(TreeModelEvent event) {
          if(event.getPath().length <= 1) {
            removeListener();
            return;
          }
          structureChanged((DebuggerTreeNodeImpl)event.getTreePath().getLastPathComponent());
        }
      }

      MyTreeModelAdapter listener = new MyTreeModelAdapter();
      listener.structureChanged((DebuggerTreeNodeImpl)getModel().getRoot());
      getModel().addTreeModelListener(listener);
    }
  }
}
