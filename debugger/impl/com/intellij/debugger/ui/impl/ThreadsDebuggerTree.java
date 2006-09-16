package com.intellij.debugger.ui.impl;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.tree.TreeModelAdapter;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import java.util.*;

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

  protected boolean isExpandable(DebuggerTreeNodeImpl node) {
    NodeDescriptorImpl descriptor = node.getDescriptor();
    if(descriptor instanceof StackFrameDescriptorImpl) {
      return false;
    }
    return descriptor.isExpandable();
  }

  protected void build(DebuggerContextImpl context) {
    buildWhenPaused(context, new RefreshThreadsTreelCommand(context));
  }

  private class RefreshThreadsTreelCommand extends RefreshDebuggerTreeCommand{
    public RefreshThreadsTreelCommand(DebuggerContextImpl context) {
      super(context);
    }

    public void threadAction() {
      super.threadAction();
      final DebuggerTreeNodeImpl root = getNodeFactory().getDefaultNode();

      final boolean showGroups = ThreadsViewSettings.getInstance().SHOW_THREAD_GROUPS;
      try {
        DebugProcessImpl debugProcess = getDebuggerContext().getDebugProcess();
        if(debugProcess == null || !debugProcess.isAttached()) return;

        final ThreadReferenceProxyImpl currentThread = ThreadsViewSettings.getInstance().SHOW_CURRENT_THREAD ?  getSuspendContext().getThread() : null;
        VirtualMachineProxyImpl vm = debugProcess.getVirtualMachineProxy();

        EvaluationContextImpl evaluationContext = getDebuggerContext().createEvaluationContext();

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
              NodeManagerImpl nodeManager = getNodeFactory();
              root.add(nodeManager.createNode(nodeManager.getThreadGroupDescriptor(null, topCurrentGroup), evaluationContext));
            }
            else {
              NodeManagerImpl nodeManager = getNodeFactory();
              root.add(nodeManager.createNode(nodeManager.getThreadDescriptor(null, currentThread), evaluationContext));
            }
          }

          for (Iterator it = vm.topLevelThreadGroups().iterator(); it.hasNext();) {
            ThreadGroupReferenceProxyImpl group = (ThreadGroupReferenceProxyImpl)it.next();
            NodeManagerImpl nodeManager = getNodeFactory();
            if(group != topCurrentGroup) {
              DebuggerTreeNodeImpl threadGroup = nodeManager.createNode(nodeManager.getThreadGroupDescriptor(null, group), evaluationContext);
              root.add(threadGroup);
            }
          }
        }
        else {
          // do not show thread groups
          if (currentThread != null) {
            NodeManagerImpl nodeManager = getNodeFactory();
            root.insert(nodeManager.createNode(nodeManager.getThreadDescriptor(null, currentThread), evaluationContext), 0);
          }
          List<ThreadReferenceProxyImpl> allThreads = new ArrayList<ThreadReferenceProxyImpl>(vm.allThreads());
          Collections.sort(allThreads, ThreadReferenceProxyImpl.ourComparator);

          for (Iterator it = allThreads.iterator(); it.hasNext();) {
            ThreadReferenceProxyImpl threadProxy = (ThreadReferenceProxyImpl)it.next();
            if (threadProxy.equals(currentThread)) {
              continue;
            }
            NodeManagerImpl nodeManager = getNodeFactory();
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

      final ThreadReferenceProxyImpl thread = getSuspendContext().getThread();
      final boolean hasThreadToSelect = thread != null; // thread can be null if pause was pressed
      final List<ThreadGroupReferenceProxyImpl> groups;
      if (hasThreadToSelect && showGroups) {
        groups = new ArrayList<ThreadGroupReferenceProxyImpl>();
        for(ThreadGroupReferenceProxyImpl group = thread.threadGroupProxy(); group != null; group = group.parent()) {
          groups.add(group);
        }
        Collections.reverse(groups);
      }
      else {
        groups = Collections.emptyList();
      }

      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          getMutableModel().setRoot(root);
          treeChanged();
          if (hasThreadToSelect) {
            selectThread(groups, thread, true);
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
