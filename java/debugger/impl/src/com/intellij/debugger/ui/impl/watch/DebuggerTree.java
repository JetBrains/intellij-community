/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * Class DebuggerTree
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.*;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.impl.DebuggerTreeBase;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.ArrayRenderer;
import com.intellij.debugger.ui.tree.render.ChildrenBuilder;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import com.sun.jdi.*;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.ExceptionEvent;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public abstract class DebuggerTree extends DebuggerTreeBase implements DataProvider {
  private static final Logger LOG = Logger.getInstance(DebuggerTree.class);
  protected static final Key<Rectangle> VISIBLE_RECT = Key.create("VISIBLE_RECT");

  public static final DataKey<DebuggerTree> DATA_KEY = DataKey.create("DebuggerTree"); 

  protected final NodeManagerImpl myNodeManager;

  private DebuggerContextImpl myDebuggerContext = DebuggerContextImpl.EMPTY_CONTEXT;

  private DebuggerTreeNodeImpl myEditedNode;

  public DebuggerTree(Project project) {
    super(null, project);
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
    model.addTreeModelListener(new TreeModelListener() {
      @Override
      public void treeNodesChanged(TreeModelEvent event) {
        hideTooltip();
      }

      @Override
      public void treeNodesInserted(TreeModelEvent event) {
        hideTooltip();
      }

      @Override
      public void treeNodesRemoved(TreeModelEvent event) {
        hideTooltip();
      }

      @Override
      public void treeStructureChanged(TreeModelEvent event) {
        hideTooltip();
      }
    });

    setModel(model);

    final TreeSpeedSearch search = new TreeSpeedSearch(this);
    search.setComparator(new SpeedSearchComparator(false));
  }

  protected NodeManagerImpl createNodeManager(Project project) {
    return new NodeManagerImpl(project, this);
  }

  @Override
  public void dispose() {
    myNodeManager.dispose();
    myDebuggerContext = DebuggerContextImpl.EMPTY_CONTEXT;
    super.dispose();
  }

  protected boolean isExpandable(DebuggerTreeNodeImpl node) {
    NodeDescriptorImpl descriptor = node.getDescriptor();
    return descriptor.isExpandable();
  }

  @Override
  public Object getData(String dataId) {
    if (DATA_KEY.is(dataId)) {
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
      DebuggerCommandImpl command = getBuildNodeCommand(node);
      if (command != null) {
        node.add(myNodeManager.createMessageNode(MessageDescriptor.EVALUATING));
        debugProcess.getManagerThread().schedule(command);
      }
    }
  }

  // todo: convert "if" into instance method call
  protected DebuggerCommandImpl getBuildNodeCommand(final DebuggerTreeNodeImpl node) {
    if (node.getDescriptor() instanceof StackFrameDescriptorImpl) {
      return new BuildStackFrameCommand(node);
    }
    else if (node.getDescriptor() instanceof ValueDescriptorImpl) {
      return new BuildValueNodeCommand(node);
    }
    else if (node.getDescriptor() instanceof StaticDescriptorImpl) {
      return new BuildStaticNodeCommand(node);
    }
    else if (node.getDescriptor() instanceof ThreadDescriptorImpl) {
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

  protected abstract void build(DebuggerContextImpl context);

  protected final void buildWhenPaused(DebuggerContextImpl context, RefreshDebuggerTreeCommand command) {
    DebuggerSession session = context.getDebuggerSession();

    if (ApplicationManager.getApplication().isUnitTestMode() || (session != null && session.getState() == DebuggerSession.STATE_PAUSED)) {
      showMessage(MessageDescriptor.EVALUATING);
      context.getDebugProcess().getManagerThread().schedule(command);
    }
    else {
      showMessage(session != null? session.getStateDescription() : DebuggerBundle.message("status.debug.stopped"));
      if (session == null || session.isStopped()) {
        getNodeFactory().clearHistory(); // save memory by clearing references on JDI objects
      }
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
    process.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        getNodeFactory().setHistoryByContext(context);
      }
      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }
    });

    build(context);
  }

  public void saveState() {
    saveState((DebuggerTreeNodeImpl)getModel().getRoot());
  }

  public void onEditorShown(DebuggerTreeNodeImpl node) {
    myEditedNode = node;
    hideTooltip();
  }

  public void onEditorHidden(DebuggerTreeNodeImpl node) {
    if (myEditedNode != null) {
      assert myEditedNode == node;
      myEditedNode = null;
    }
  }

  @Override
  public JComponent createToolTip(MouseEvent e) {
    return myEditedNode != null ? null : super.createToolTip(e);
  }

  protected abstract static class RefreshDebuggerTreeCommand extends SuspendContextCommandImpl {
    private final DebuggerContextImpl myDebuggerContext;

    @Override
    public Priority getPriority() {
      return Priority.NORMAL;
    }

    public RefreshDebuggerTreeCommand(DebuggerContextImpl context) {
      super(context.getSuspendContext());
      myDebuggerContext = context;
    }

    public final DebuggerContextImpl getDebuggerContext() {
      return myDebuggerContext;
    }
  }

  public DebuggerContextImpl getDebuggerContext() {
    return myDebuggerContext;
  }

  public abstract class BuildNodeCommand extends DebuggerContextCommandImpl {
    private final DebuggerTreeNodeImpl myNode;

    protected final List<DebuggerTreeNodeImpl> myChildren = new LinkedList<DebuggerTreeNodeImpl>();

    protected BuildNodeCommand(DebuggerTreeNodeImpl node) {
      super(DebuggerTree.this.getDebuggerContext());
      myNode = node;
    }

    @Override
    public Priority getPriority() {
      return Priority.NORMAL;
    }

    public DebuggerTreeNodeImpl getNode() {
      return myNode;
    }

    protected void updateUI(final boolean scrollToVisible) {
      DebuggerInvocationUtil.swingInvokeLater(getProject(), new Runnable() {
        @Override
        public void run() {
          myNode.removeAllChildren();
          for (DebuggerTreeNodeImpl debuggerTreeNode : myChildren) {
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

    @Override
    public void threadAction() {
      try {
        final StackFrameDescriptorImpl stackDescriptor = (StackFrameDescriptorImpl)getNode().getDescriptor();
        final StackFrameProxyImpl frame = stackDescriptor.getFrameProxy();

        final DebuggerContextImpl debuggerContext = getDebuggerContext();
        final EvaluationContextImpl evaluationContext = debuggerContext.createEvaluationContext();
        if (!debuggerContext.isEvaluationPossible()) {
          myChildren.add(myNodeManager.createNode(MessageDescriptor.EVALUATION_NOT_POSSIBLE, evaluationContext));
        }

        final Location location = frame.location();
        LOG.assertTrue(location != null);

        final ObjectReference thisObjectReference = frame.thisObject();
        final NodeDescriptor descriptor;
        if (thisObjectReference != null) {
          descriptor = myNodeManager.getThisDescriptor(stackDescriptor, thisObjectReference);
        }
        else {
          descriptor = myNodeManager.getStaticDescriptor(stackDescriptor, location.method().declaringType());
        }
        myChildren.add(myNodeManager.createNode(descriptor, evaluationContext));

        final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
        if (classRenderer.SHOW_VAL_FIELDS_AS_LOCAL_VARIABLES) {
          if (thisObjectReference != null && evaluationContext.getDebugProcess().getVirtualMachineProxy().canGetSyntheticAttribute())  {
            final ReferenceType thisRefType = thisObjectReference.referenceType();
            if (thisRefType instanceof ClassType && thisRefType.equals(location.declaringType()) && thisRefType.name().contains("$")) { // makes sense for nested classes only
              final ClassType clsType = (ClassType)thisRefType;
              for (Field field : clsType.fields()) {
                if (DebuggerUtils.isSynthetic(field) && StringUtil.startsWith(field.name(), FieldDescriptorImpl.OUTER_LOCAL_VAR_FIELD_PREFIX)) {
                  final FieldDescriptorImpl fieldDescriptor = myNodeManager.getFieldDescriptor(stackDescriptor, thisObjectReference, field);
                  myChildren.add(myNodeManager.createNode(fieldDescriptor, evaluationContext));
                }
              }
            }
          }
        }

        try {
          buildVariables(stackDescriptor, evaluationContext);
          if (XDebuggerSettingsManager.getInstance().getDataViewSettings().isSortValues()) {
            Collections.sort(myChildren, NodeManagerImpl.getNodeComparator());
          }
        }
        catch (EvaluateException e) {
          myChildren.add(myNodeManager.createMessageNode(new MessageDescriptor(e.getMessage())));
        }
        // add last method return value if any
        final Pair<Method, Value> methodValuePair = debuggerContext.getDebugProcess().getLastExecutedMethod();
        if (methodValuePair != null) {
          ValueDescriptorImpl returnValueDescriptor = myNodeManager.getMethodReturnValueDescriptor(stackDescriptor, methodValuePair.getFirst(), methodValuePair.getSecond());
          myChildren.add(1, myNodeManager.createNode(returnValueDescriptor, evaluationContext));
        }
        // add context exceptions
        for (Pair<Breakpoint, Event> pair : DebuggerUtilsEx.getEventDescriptors(getSuspendContext())) {
          final Event debugEvent = pair.getSecond();
          if (debugEvent instanceof ExceptionEvent) {
            final ObjectReference exception = ((ExceptionEvent)debugEvent).exception();
            if (exception != null) {
              final ValueDescriptorImpl exceptionDescriptor = myNodeManager.getThrownExceptionObjectDescriptor(stackDescriptor, exception);
              final DebuggerTreeNodeImpl exceptionNode = myNodeManager.createNode(exceptionDescriptor, evaluationContext);
              myChildren.add(1, exceptionNode);
            }
          }
        }

      }
      catch (EvaluateException e) {
        myChildren.clear();
        myChildren.add(myNodeManager.createMessageNode(new MessageDescriptor(e.getMessage())));
      }
      catch (InvalidStackFrameException e) {
        LOG.info(e);
        myChildren.clear();
        notifyCancelled();
      }
      catch (InternalException e) {
        if (e.errorCode() == 35) {
          myChildren.add(
            myNodeManager.createMessageNode(new MessageDescriptor(DebuggerBundle.message("error.corrupt.debug.info", e.getMessage()))));
        }
        else {
          throw e;
        }
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

  private class BuildValueNodeCommand extends BuildNodeCommand implements ChildrenBuilder {
    public BuildValueNodeCommand(DebuggerTreeNodeImpl node) {
      super(node);
    }

    @Override
    public void threadAction() {
      final DebuggerTreeNodeImpl node = getNode();
      ValueDescriptorImpl descriptor = (ValueDescriptorImpl)node.getDescriptor();
      try {
        final NodeRenderer renderer = descriptor.getRenderer(getSuspendContext().getDebugProcess());
        renderer.buildChildren(descriptor.getValue(), this, getDebuggerContext().createEvaluationContext());
      }
      catch (ObjectCollectedException e) {
        final String message = e.getMessage();
        DebuggerInvocationUtil.swingInvokeLater(getProject(), new Runnable() {
          @Override
          public void run() {
            node.removeAllChildren();
            node.add(getNodeFactory().createMessageNode(
              new MessageDescriptor(DebuggerBundle.message("error.cannot.build.node.children.object.collected", message)))
            );
            node.childrenChanged(false);
          }
        });
      }
    }

    @Override
    public NodeManagerImpl getNodeManager() {

      return myNodeManager;
    }

    @Override
    public NodeManagerImpl getDescriptorManager() {
      return myNodeManager;
    }

    @Override
    public ValueDescriptorImpl getParentDescriptor() {
      return (ValueDescriptorImpl)getNode().getDescriptor();
    }

    @Override
    public void setRemaining(int remaining) {}

    @Override
    public void initChildrenArrayRenderer(ArrayRenderer renderer) {}

    @Override
    public void setChildren(final List<DebuggerTreeNode> children) {
      for (DebuggerTreeNode child : children) {
        if (child instanceof DebuggerTreeNodeImpl) {
          myChildren.add(((DebuggerTreeNodeImpl)child));
        }
      }
      updateUI(false);
    }
  }

  private class BuildStaticNodeCommand extends BuildNodeCommand {
    public BuildStaticNodeCommand(DebuggerTreeNodeImpl node) {
      super(node);
    }

    @Override
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

  private class BuildThreadCommand extends BuildNodeCommand {
    public BuildThreadCommand(DebuggerTreeNodeImpl threadNode) {
      super(threadNode);
    }

    @Override
    public void threadAction() {
      ThreadDescriptorImpl threadDescriptor = ((ThreadDescriptorImpl)getNode().getDescriptor());
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
            //  private static void longMethod(){
            //    try {
            //      Thread.sleep(100000);
            //    } catch (InterruptedException e) {
            //      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
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
    protected final List<DebuggerTreeNodeImpl> myChildren = new LinkedList<DebuggerTreeNodeImpl>();

    public BuildThreadGroupCommand(DebuggerTreeNodeImpl node) {
      myNode = node;
    }

    @Override
    protected void action() throws Exception {
      ThreadGroupDescriptorImpl groupDescriptor = (ThreadGroupDescriptorImpl)myNode.getDescriptor();
      ThreadGroupReferenceProxyImpl threadGroup = groupDescriptor.getThreadGroupReference();

      List<ThreadReferenceProxyImpl> threads = new ArrayList<ThreadReferenceProxyImpl>(threadGroup.threads());
      Collections.sort(threads, ThreadReferenceProxyImpl.ourComparator);

      final DebuggerContextImpl debuggerContext = getDebuggerContext();
      final SuspendContextImpl suspendContext = debuggerContext.getSuspendContext();
      final EvaluationContextImpl evaluationContext = suspendContext != null && !suspendContext.isResumed()? debuggerContext.createEvaluationContext() : null;

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
      DebuggerInvocationUtil.swingInvokeLater(getProject(), new Runnable() {
        @Override
        public void run() {
          myNode.removeAllChildren();
          for (DebuggerTreeNodeImpl debuggerTreeNode : myChildren) {
            myNode.add(debuggerTreeNode);
          }
          myNode.childrenChanged(scrollToVisible);
        }
      });
    }
  }

  public void hideTooltip() {
    myTipManager.hideTooltip();
  }
}
