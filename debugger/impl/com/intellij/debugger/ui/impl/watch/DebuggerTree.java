/*
 * Class DebuggerTree
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.InvokeThread;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.DebuggerTreeBase;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.ChildrenBuilder;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.debugger.ui.tree.render.NodeRendererSettingsListener;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.TreeSpeedSearch;
import com.sun.jdi.*;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

public abstract class DebuggerTree extends DebuggerTreeBase implements DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.DebuggerTree");
  protected static final Key<Rectangle> VISIBLE_RECT = Key.create("VISIBLE_RECT");

  private final Project myProject;
  protected final NodeManagerImpl myNodeManager;

  private NodeRendererSettingsListener mySettingsListener;
  private DebuggerContextImpl myDebuggerContext = DebuggerContextImpl.EMPTY_CONTEXT;

  public DebuggerTree(Project project) {
    super(null, project);
    setScrollsOnExpand(false);
    myNodeManager = new NodeManagerImpl(project, this);
    final TreeBuilder model = new TreeBuilder(this) {
      public void buildChildren(TreeBuilderNode node) {
        final DebuggerTreeNodeImpl debuggerTreeNode = (DebuggerTreeNodeImpl)node;
        if (debuggerTreeNode.getDescriptor() instanceof DefaultNodeDescriptor) {
          return;
        }
        buildNode(debuggerTreeNode);
      }

      public boolean isExpandable(TreeBuilderNode builderNode) {
        return DebuggerTree.this.isExpandable((DebuggerTreeNodeImpl)builderNode);
      }
    };
    model.setRoot(getNodeFactory().getDefaultNode());
    model.addTreeModelListener(new TreeModelListener() {
      public void treeNodesChanged     (TreeModelEvent event) { myTipManager.hideTooltip(); }
      public void treeNodesInserted    (TreeModelEvent event) { myTipManager.hideTooltip(); }
      public void treeNodesRemoved     (TreeModelEvent event) { myTipManager.hideTooltip(); }
      public void treeStructureChanged (TreeModelEvent event) { myTipManager.hideTooltip(); }
    });

    setModel(model);

    myProject = project;
    final TreeSpeedSearch search = new TreeSpeedSearch(this);
    search.setComparator(new SpeedSearchBase.SpeedSearchComparator() {
      public void translatePattern(final StringBuilder buf, final String pattern) {
        final int len = pattern.length();
        for (int i = 0; i < len; ++i) {
          translateCharacter(buf, pattern.charAt(i));
        }
      }
    });
  }

  public void dispose() {
    myNodeManager.dispose();
    myDebuggerContext = DebuggerContextImpl.EMPTY_CONTEXT;
    super.dispose();
  }

  private void installSettingsListener() {
    if (mySettingsListener != null) {
      return;
    }
    mySettingsListener = new NodeRendererSettingsListener() {
      private void rendererSettingsChanged(DebuggerTreeNodeImpl node) {
        final NodeDescriptorImpl nodeDescriptor = node.getDescriptor();
        if (nodeDescriptor instanceof ValueDescriptorImpl || nodeDescriptor instanceof StackFrameDescriptorImpl) {
          node.calcRepresentation();
        }

        final Enumeration e = node.rawChildren();
        while (e.hasMoreElements()) {
          DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)e.nextElement();
          rendererSettingsChanged(child);
        }
      }

      public void renderersChanged() {
        DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getModel().getRoot();
        if (root != null) {
          rendererSettingsChanged(root);
        }

      }
    };
    NodeRendererSettings.getInstance().addListener(mySettingsListener);
  }

  private void uninstallSettingsListener() {
    if (mySettingsListener == null) {
      return;
    }
    NodeRendererSettings.getInstance().removeListener(mySettingsListener);
    mySettingsListener = null;
  }

  public void removeNotify() {
    uninstallSettingsListener();
    super.removeNotify();
  }

  public void addNotify() {
    super.addNotify();
    installSettingsListener();
  }

  protected boolean isExpandable(DebuggerTreeNodeImpl node) {
    NodeDescriptorImpl descriptor = node.getDescriptor();
    return descriptor.isExpandable();
  }

  public Object getData(String dataId) {
    if (DebuggerActions.DEBUGGER_TREE.equals(dataId)) {
      return this;
    }
    return null;
  }


  private void buildNode(final DebuggerTreeNodeImpl node) {
    if (node == null || node.getDescriptor() == null) {
      return;
    }
    final DebugProcessImpl debugProcess = getDebuggerContext().getDebugProcess();
    if (debugProcess != null) {
      BuildNodeCommand command = getBuildNodeCommand(node);
      command.getNode().add(myNodeManager.createMessageNode(MessageDescriptor.EVALUATING));
      debugProcess.getManagerThread().invokeLater(command, InvokeThread.Priority.NORMAL);
    }
  }

  protected BuildNodeCommand getBuildNodeCommand(final DebuggerTreeNodeImpl node) {
    if (node.getDescriptor() instanceof StackFrameDescriptorImpl) {
      return new BuildStackFrameCommand(node);
    }
    else if (node.getDescriptor() instanceof ValueDescriptorImpl) {
      return new BuildValueNodeCommand(node);
    }
    else if (node.getDescriptor() instanceof StaticDescriptorImpl) {
      return new BuildStaticNodeCommand(node);
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
      if(rowBounds != null && getVisibleRect().contains(rowBounds)) {
        node.getDescriptor().putUserData(VISIBLE_RECT, getVisibleRect());
        node.getDescriptor().myIsVisible = true;
      }
      else {
        node.getDescriptor().putUserData(VISIBLE_RECT, null);
        node.getDescriptor().myIsVisible = false;
      }
    }

    for (Enumeration e = node.rawChildren(); e.hasMoreElements();) {
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
    for (int idx = rowCount - 1; idx >=0; idx--) {
      final TreePath treePath = getPathForRow(idx);
      if (treePath != null) {
        if (!rootPath.isDescendant(treePath)) {
          continue;
        }
        final DebuggerTreeNodeImpl pathNode = (DebuggerTreeNodeImpl)treePath.getLastPathComponent();
        final NodeDescriptorImpl descriptor = pathNode.getDescriptor();

        if (descriptor != null && descriptor.myIsVisible) {
          final Rectangle visibleRect = descriptor.getUserData(VISIBLE_RECT);
          if(visibleRect != null) {
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

  public void scrollRectToVisible(Rectangle aRect) {
    // see IDEADEV-432
    aRect.width += aRect.x;
    aRect.x = 0;
    super.scrollRectToVisible(aRect);
  }

  private void restoreStateImpl(DebuggerTreeNodeImpl node) {
    restoreNodeState(node);
    if (node.getDescriptor().myIsExpanded) {
      for (Enumeration e = node.rawChildren(); e.hasMoreElements();) {
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

  public void showMessage(String messageText) {
    showMessage(new MessageDescriptor(messageText));
  }

  public final void treeChanged() {
    DebuggerTreeNodeImpl node = (DebuggerTreeNodeImpl)getModel().getRoot();
    if (node != null) {
      getMutableModel().nodeStructureChanged(node);
      restoreState();
    }
  }

  public Project getProject() { return myProject; }

  protected abstract void build(DebuggerContextImpl context);

  protected final void buildWhenPaused(DebuggerContextImpl context, RefreshDebuggerTreeCommand command) {
    DebuggerSession debuggerSession = context.getDebuggerSession();

    if(ApplicationManager.getApplication().isUnitTestMode() || debuggerSession.getState() == DebuggerSession.STATE_PAUSED) {
      showMessage(MessageDescriptor.EVALUATING);
      context.getDebugProcess().getManagerThread().invokeLater(command, InvokeThread.Priority.NORMAL);
    }
    else {
      showMessage(context.getDebuggerSession().getStateDescription());
    }
  }

  public void rebuild(final DebuggerContextImpl context) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final DebugProcessImpl process = context.getDebugProcess();
    if (process == null) {
      return; // empty context, no process available yet
    }
    myDebuggerContext = context;
    saveState();
    process.getManagerThread().invokeLater(new DebuggerContextCommandImpl(context){
      public void threadAction() {
        getNodeFactory().setHistoryByContext(context);
      }
    }, InvokeThread.Priority.NORMAL);

    build(context);
  }

  public void saveState() {
    saveState((DebuggerTreeNodeImpl)getModel().getRoot());
  }

  protected abstract static class RefreshDebuggerTreeCommand extends DebuggerContextCommandImpl {
    public RefreshDebuggerTreeCommand(DebuggerContextImpl context) {
      super(context);
    }
    public void threadAction() {
    }
  }

  public DebuggerContextImpl getDebuggerContext() {
    return myDebuggerContext;
  }

  public abstract class BuildNodeCommand extends DebuggerContextCommandImpl {
    private final DebuggerTreeNodeImpl myNode;

    protected final List<DebuggerTreeNode> myChildren = new LinkedList<DebuggerTreeNode>();

    protected BuildNodeCommand(DebuggerTreeNodeImpl node) {
      super(DebuggerTree.this.getDebuggerContext());
      myNode = node;
    }

    public DebuggerTreeNodeImpl getNode() {
      return myNode;
    }

    protected void updateUI(final boolean scrollToVisible) {
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          myNode.removeAllChildren();
          for (DebuggerTreeNode debuggerTreeNode : myChildren) {
            myNode.add(debuggerTreeNode);
          }
          myNode.childrenChanged(scrollToVisible);
        }
      });
    }
  }

  protected class BuildStackFrameCommand extends BuildNodeCommand {
    public BuildStackFrameCommand(DebuggerTreeNodeImpl stackNode) {
      super(stackNode);
    }

    public final void threadAction() {
      try {
        final StackFrameDescriptorImpl stackDescriptor = (StackFrameDescriptorImpl)getNode().getDescriptor();
        final StackFrameProxyImpl frame = stackDescriptor.getFrameProxy();
        if (frame == null) {
          return;
        }
        final Location location = frame.location();

        final ObjectReference thisObjectReference = frame.thisObject();

        final DebuggerContextImpl debuggerContext = getDebuggerContext();
        final EvaluationContextImpl evaluationContext = debuggerContext.createEvaluationContext();

        if (!debuggerContext.isEvaluationPossible()) {
          myChildren.add(myNodeManager.createNode(MessageDescriptor.EVALUATION_NOT_POSSIBLE, evaluationContext));
        }

        final NodeDescriptor descriptor;
        if (thisObjectReference != null) {
          descriptor = myNodeManager.getThisDescriptor(stackDescriptor, thisObjectReference);
        }
        else {
          final ReferenceType type = location.method().declaringType();
          descriptor = myNodeManager.getStaticDescriptor(stackDescriptor, type);
        }
        myChildren.add(myNodeManager.createNode(descriptor, evaluationContext));
        try {
          buildVariables(stackDescriptor, evaluationContext);
          if (NodeRendererSettings.getInstance().getClassRenderer().SORT_ASCENDING) {
            Collections.sort(myChildren, NodeManagerImpl.getNodeComparator());
          }
        }
        catch (EvaluateException e) {
          myChildren.add(myNodeManager.createMessageNode(new MessageDescriptor(e.getMessage())));
        }
        // add last method return value if any
        final Pair<Method,Value> methodValuePair = debuggerContext.getDebugProcess().getLastExecutedMethod();
        if (methodValuePair != null) {
          final ValueDescriptorImpl returnValueDescriptor =
            myNodeManager.getMethodReturnValueDescriptor(stackDescriptor, methodValuePair.getFirst(), methodValuePair.getSecond());
          final DebuggerTreeNodeImpl methodReturnValueNode = myNodeManager.createNode(returnValueDescriptor, evaluationContext);
          myChildren.add(1, methodReturnValueNode);
        }
      }
      catch (EvaluateException e) {
        myChildren.clear();
        myChildren.add(myNodeManager.createMessageNode(new MessageDescriptor(e.getMessage())));
      }

      updateUI(true);
    }

    protected void buildVariables(final StackFrameDescriptorImpl stackDescriptor, final EvaluationContextImpl evaluationContext) throws EvaluateException {
      final StackFrameProxyImpl frame = stackDescriptor.getFrameProxy();
      for (final LocalVariableProxyImpl local : frame.visibleVariables()) {
        final LocalVariableDescriptorImpl localVariableDescriptor = myNodeManager.getLocalVariableDescriptor(stackDescriptor, local);
        final DebuggerTreeNodeImpl variableNode = myNodeManager.createNode(localVariableDescriptor, evaluationContext);
        myChildren.add(variableNode);
      }
    }
  }

  /*
  private class BuildThreadGroupCommand extends BuildNodeCommand {
    public BuildThreadGroupCommand(DebuggerTreeNodeImpl node) {
      super(node);
    }

    public void threadAction() {
      ThreadGroupDescriptorImpl groupDescriptor = (ThreadGroupDescriptorImpl)getNode().getDescriptor();
      ThreadGroupReferenceProxyImpl threadGroup = groupDescriptor.getThreadGroupReference();

      List<ThreadReferenceProxyImpl> threads = new ArrayList<ThreadReferenceProxyImpl>(threadGroup.threads());
      Collections.sort(threads, ThreadReferenceProxyImpl.ourComparator);

      EvaluationContextImpl evaluationContext = getDebuggerContext().createEvaluationContext();

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

      ArrayList<DebuggerTreeNodeImpl> threadNodes = new ArrayList<DebuggerTreeNodeImpl>();

      for (ThreadReferenceProxyImpl thread : threads) {
        if (thread != null) {
          DebuggerTreeNodeImpl threadNode =
            myNodeManager.createNode(myNodeManager.getThreadDescriptor(groupDescriptor, thread), evaluationContext);
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
  }
  */

  private class BuildValueNodeCommand extends BuildNodeCommand implements ChildrenBuilder {
    public BuildValueNodeCommand(DebuggerTreeNodeImpl node) {
      super(node);
    }

    public void threadAction() {
      ValueDescriptorImpl descriptor = (ValueDescriptorImpl)getNode().getDescriptor();
      try {
        final NodeRenderer renderer = descriptor.getRenderer(getSuspendContext().getDebugProcess());
        renderer.buildChildren(descriptor.getValue(), this, getDebuggerContext().createEvaluationContext());
      }
      catch (ObjectCollectedException e) {
        getNode().removeAllChildren();
        getNode().add(getNodeFactory().createMessageNode(new MessageDescriptor(
          DebuggerBundle.message("error.cannot.build.node.children.object.collected", e.getMessage())))
        );
        getNode().childrenChanged(false);
      }
    }

    public NodeManagerImpl getNodeManager() {
      
      return myNodeManager;
    }

    public NodeManagerImpl getDescriptorManager() {
      return myNodeManager;
    }

    public ValueDescriptorImpl getParentDescriptor() {
      return (ValueDescriptorImpl)getNode().getDescriptor();
    }

    public void setChildren(final List<DebuggerTreeNode> children) {
      myChildren.addAll(children);
      updateUI(false);
    }
  }

  private class BuildStaticNodeCommand extends BuildNodeCommand {
    public BuildStaticNodeCommand(DebuggerTreeNodeImpl node) {
      super(node);
    }

    public void threadAction() {
      final StaticDescriptorImpl sd = (StaticDescriptorImpl)getNode().getDescriptor();
      final ReferenceType refType = sd.getType();
      List<Field> fields = refType.allFields();
      for (Field field : fields) {
        if (field.isStatic()) {
          final FieldDescriptorImpl fieldDescriptor = myNodeManager.getFieldDescriptor(sd, null, field);
          final EvaluationContextImpl evaluationContext = getDebuggerContext().createEvaluationContext();
          final DebuggerTreeNodeImpl node = myNodeManager.createNode(fieldDescriptor, evaluationContext);
          myChildren.add(node);
        }
      }

      updateUI(true);
    }
  }

}