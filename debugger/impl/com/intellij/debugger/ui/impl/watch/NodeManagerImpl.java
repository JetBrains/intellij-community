package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.impl.nodes.NodeComparator;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.NodeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.event.Event;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 ** finds correspondence between new descriptor and one created on the previous steps
 ** stores maximum  CACHED_STEPS steps
 ** call saveState function to start new step
 */

public class NodeManagerImpl extends NodeDescriptorFactoryImpl implements NodeManager{
  private static Comparator ourNodeComparator = new NodeComparator();

  private final DebuggerTree myDebuggerTree;
  private List<Breakpoint> myBreakpoints = Collections.emptyList();

  public NodeManagerImpl(Project project, DebuggerTree tree) {
    super(project);
    myDebuggerTree = tree;
  }

  public static Comparator getNodeComparator() {
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
    final DebugProcessImpl debugProcess = context.getDebugProcess();
    final DescriptorHistoryManager descriptorHistoryManager = debugProcess.getDescriptorHistoryManager();
    descriptorHistoryManager.storeHistory(new DescriptorHistory(getCurrentHistoryTree(), myBreakpoints));

    final DescriptorHistory history = descriptorHistoryManager.restoreHistory(context);
    final DescriptorTree descriptorTree = (history != null)? history.getDescriptorTree() : new DescriptorTree(true);

    deriveHistoryTree(descriptorTree, context);

    List<Pair<Breakpoint, Event>> eventDescriptors = DebuggerUtilsEx.getEventDescriptors(context.getSuspendContext());
    myBreakpoints = ContainerUtil.map(eventDescriptors, new Function<Pair<Breakpoint, Event>, Breakpoint>() {
      public Breakpoint fun(Pair<Breakpoint, Event> o) {
        return o.getFirst();
      }
    });
  }

  private DebuggerTree getTree() {
    return myDebuggerTree;
  }
}
