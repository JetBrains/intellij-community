package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.nodes.NodeComparator;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.NodeManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;

import java.util.Comparator;
import java.util.Map;

/**
 ** finds correspondence between new descriptor and one created on the previous steps
 ** stores maximum  CACHED_STEPS steps
 ** call saveState function to start new step
 */

public class NodeManagerImpl extends NodeDescriptorFactoryImpl implements NodeManager{
  private static Comparator<DebuggerTreeNode> ourNodeComparator = new NodeComparator();

  private final DebuggerTree myDebuggerTree;
  private Long myThreadId = new Long(-1);
  private Map<Long, DescriptorTree> myHistories = new HashMap<Long, DescriptorTree>();

  public NodeManagerImpl(Project project, DebuggerTree tree) {
    super(project);
    myDebuggerTree = tree;
  }

  public static Comparator<DebuggerTreeNode> getNodeComparator() {
    return ourNodeComparator;
  }

  public DebuggerTreeNodeImpl createNode(NodeDescriptor descriptor, EvaluationContext evaluationContext) {
    ((NodeDescriptorImpl)descriptor).setContext((EvaluationContextImpl)evaluationContext);
    return DebuggerTreeNodeImpl.createNode(getTree(), (NodeDescriptorImpl)descriptor, (EvaluationContextImpl)evaluationContext);
  }

  public DebuggerTreeNodeImpl getDefaultNode() {
    return DebuggerTreeNodeImpl.createNodeNoUpdate(getTree(), new DefaultNodeDescriptor());
  }

  public DebuggerTreeNodeImpl createMessageNode(MessageDescriptor descriptor) {
    return DebuggerTreeNodeImpl.createNodeNoUpdate(getTree(), descriptor);
  }

  public DebuggerTreeNodeImpl createMessageNode(String message) {
    return DebuggerTreeNodeImpl.createNodeNoUpdate(getTree(), new MessageDescriptor(message));
  }

  public void setHistoryByContext(final DebuggerContextImpl context) {
    myHistories.put(myThreadId, getCurrentHistoryTree());

    final long contextThreadId = context.getThreadProxy().uniqueID();
    final DescriptorTree historyTree = myHistories.get(contextThreadId);
    final DescriptorTree descriptorTree = (historyTree != null)? historyTree : new DescriptorTree(true);

    deriveHistoryTree(descriptorTree, context);
    myThreadId = contextThreadId;
  }

  public void dispose() {
    myHistories.clear();
    super.dispose();
  }

  private DebuggerTree getTree() {
    return myDebuggerTree;
  }
}
