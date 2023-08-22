// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.JvmtiError;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.nodes.NodeComparator;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.NodeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.sun.jdi.InternalException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * <ul>
 * <li>finds correspondence between new descriptor and one created on the previous steps
 * <li>stores maximum CACHED_STEPS steps
 * <li>call saveState function to start new step
 * </ul>
 */
public class NodeManagerImpl extends NodeDescriptorFactoryImpl implements NodeManager {
  private static final Comparator<DebuggerTreeNode> ourNodeComparator = new NodeComparator();

  private final DebuggerTree myDebuggerTree;
  private String myHistoryKey = null;
  private final Map<String, DescriptorTree> myHistories = new HashMap<>();

  public NodeManagerImpl(Project project, DebuggerTree tree) {
    super(project);
    myDebuggerTree = tree;
  }

  public static Comparator<DebuggerTreeNode> getNodeComparator() {
    return ourNodeComparator;
  }

  @Override
  @NotNull
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

  @Override
  @NotNull
  public DebuggerTreeNodeImpl createMessageNode(@NlsContexts.Label String message) {
    return DebuggerTreeNodeImpl.createNodeNoUpdate(getTree(), new MessageDescriptor(message));
  }

  public void setHistoryByContext(final DebuggerContextImpl context) {
    setHistoryByContext(context.getFrameProxy());
  }

  public void setHistoryByContext(StackFrameProxyImpl frameProxy) {
    if (myHistoryKey != null) {
      myHistories.put(myHistoryKey, getCurrentHistoryTree());
    }

    final String historyKey = getContextKey(frameProxy);
    final DescriptorTree descriptorTree;
    if (historyKey != null) {
      final DescriptorTree historyTree = myHistories.get(historyKey);
      descriptorTree = (historyTree != null) ? historyTree : new DescriptorTree(true);
    }
    else {
      descriptorTree = new DescriptorTree(true);
    }

    deriveHistoryTree(descriptorTree, frameProxy);
    myHistoryKey = historyKey;
  }


  @Nullable
  public String getContextKey(final StackFrameProxyImpl frame) {
    return getContextKeyForFrame(frame);
  }

  @Nullable
  public static String getContextKeyForFrame(final StackFrameProxyImpl frame) {
    if (frame == null) {
      return null;
    }
    try {
      final Location location = frame.location();
      final Method method = DebuggerUtilsEx.getMethod(location);
      if (method == null) {
        return null;
      }
      return location.declaringType().signature() + "#" + method.name() + method.signature();
    }
    catch (EvaluateException ignored) {
    }
    catch (InternalException ie) {
      if (ie.errorCode() != JvmtiError.INVALID_METHODID) {
        throw ie;
      }
    }
    return null;
  }

  @Override
  public void dispose() {
    myHistories.clear();
    super.dispose();
  }

  private DebuggerTree getTree() {
    return myDebuggerTree;
  }
}
