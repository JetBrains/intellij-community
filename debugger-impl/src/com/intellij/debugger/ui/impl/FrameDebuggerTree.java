/*
 * Class FrameDebuggerTree
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.engine.SuspendManager;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.ViewsGeneralSettings;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.tree.TreeModelAdapter;

import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeModel;
import java.util.Enumeration;

public class FrameDebuggerTree extends DebuggerTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.FrameDebuggerTree");
  private boolean myAnyNewLocals;

  public FrameDebuggerTree(Project project) {
    super(project);
  }

  protected void build(DebuggerContextImpl context) {
    myAnyNewLocals = false;
    buildWhenPaused(context, new RefreshFrameTreeCommand(context));
  }

  public void restoreNodeState(DebuggerTreeNodeImpl node) {
    if(myAnyNewLocals) {
      final NodeDescriptorImpl descriptor = node.getDescriptor();
      final boolean isLocalVar = descriptor instanceof LocalVariableDescriptorImpl;
      descriptor.myIsSelected &= isLocalVar;
      // override this setting so that tree will scroll to new locals
      descriptor.myIsVisible = isLocalVar && descriptor.myIsSelected;
      if (!descriptor.myIsVisible) {
        descriptor.putUserData(VISIBLE_RECT, null);
      }
    }
    super.restoreNodeState(node);
    if(myAnyNewLocals && node.getDescriptor().myIsExpanded) {
      DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getMutableModel().getRoot();
      scrollToVisible(root);
    }
  }

  private class RefreshFrameTreeCommand extends RefreshDebuggerTreeCommand {
    public RefreshFrameTreeCommand(DebuggerContextImpl context) {
      super(context);
    }

    public void threadAction() {
      super.threadAction();
      DebuggerTreeNodeImpl rootNode;

      final ThreadReferenceProxyImpl currentThread = getDebuggerContext().getThreadProxy();
      if(currentThread == null) {
        return;
      }

      try {
        StackFrameProxyImpl frame = getDebuggerContext().getFrameProxy();

        if (frame != null) {
          NodeManagerImpl nodeManager = getNodeFactory();
          rootNode = nodeManager.createNode(nodeManager.getStackFrameDescriptor(null, frame), getDebuggerContext().createEvaluationContext());
        }
        else {
          rootNode = getNodeFactory().getDefaultNode();
          SuspendManager suspendManager = getSuspendContext().getDebugProcess().getSuspendManager();
          if(suspendManager.isSuspended(currentThread)) {
            try {
              if(currentThread.frameCount() == 0) {
                rootNode.add(MessageDescriptor.THREAD_IS_EMPTY);
              }
              else {
                rootNode.add(MessageDescriptor.DEBUG_INFO_UNAVAILABLE);
              }
            }
            catch (EvaluateException e) {
              rootNode.add(new MessageDescriptor(e.getMessage()));
            }
          }
          else {
            rootNode.add(MessageDescriptor.THREAD_IS_RUNNING);
          }
        }
      }
      catch (Exception ex) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(ex);
        }
        rootNode = getNodeFactory().getDefaultNode();
        rootNode.add(MessageDescriptor.DEBUG_INFO_UNAVAILABLE);
      }

      final DebuggerTreeNodeImpl rootNode1 = rootNode;
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          getMutableModel().setRoot(rootNode1);
          treeChanged();

          final TreeModel model = getModel();
          model.addTreeModelListener(new TreeModelAdapter() {
            public void treeStructureChanged(TreeModelEvent e) {
              final Object[] path = e.getPath();
              if(path.length > 0 && path[path.length - 1] == rootNode1) {
                // wait until rootNode1 (the root just set) becomes the root
                model.removeTreeModelListener(this);
                if (ViewsGeneralSettings.getInstance().AUTOSCROLL_TO_NEW_LOCALS) {
                  autoscrollToNewLocals(rootNode1);
                }
                else {
                  // should clear this flag, otherwise, if AUTOSCROLL_TO_NEW_LOCALS option turned
                  // to true during the debug process, all these variables will be considered 'new'
                  for (Enumeration children  = rootNode1.rawChildren(); children.hasMoreElements();) {
                    final DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)children.nextElement();
                    final NodeDescriptorImpl descriptor = child.getDescriptor();
                    if(descriptor instanceof LocalVariableDescriptorImpl) {
                      ((LocalVariableDescriptorImpl)descriptor).setNewLocal(false);
                    }
                  }
                }
              }
            }
          });
        }
        private void autoscrollToNewLocals(DebuggerTreeNodeImpl frameNode) {
          final DebuggerSession debuggerSession = getDebuggerContext().getDebuggerSession();
          final boolean isSteppingThrough = debuggerSession.isSteppingThrough(getDebuggerContext().getThreadProxy());
          for (Enumeration e  = frameNode.rawChildren(); e.hasMoreElements();) {
            final DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)e.nextElement();
            final NodeDescriptorImpl descriptor = child.getDescriptor();
            if(!(descriptor instanceof LocalVariableDescriptorImpl)) {
              continue;
            }
            final LocalVariableDescriptorImpl localVariableDescriptor = (LocalVariableDescriptorImpl)descriptor;
            if (isSteppingThrough && localVariableDescriptor.isNewLocal()) {
              TreePath treePath = new TreePath(child.getPath());
              addSelectionPath(treePath);
              myAnyNewLocals = true;
              descriptor.myIsSelected = true;
            }
            else {
              removeSelectionPath(new TreePath(child.getPath()));
              descriptor.myIsSelected = false;
            }
            localVariableDescriptor.setNewLocal(false);
          }
        }
      });
    }
  }

}