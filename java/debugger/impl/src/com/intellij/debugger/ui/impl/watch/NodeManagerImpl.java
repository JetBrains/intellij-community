/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.nodes.NodeComparator;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.NodeManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.HashMap;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;

/**
 ** finds correspondence between new descriptor and one created on the previous steps
 ** stores maximum  CACHED_STEPS steps
 ** call saveState function to start new step
 */

public class NodeManagerImpl extends NodeDescriptorFactoryImpl implements NodeManager{
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
      descriptorTree = (historyTree != null)? historyTree : new DescriptorTree(true);
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
      final Method method = location.method();
      final ReferenceType referenceType = location.declaringType();
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        return builder.append(referenceType.signature()).append("#").append(method.name()).append(method.signature()).toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    catch (EvaluateException e) {
      return null;
    }
  }

  public void dispose() {
    clearHistory();
    super.dispose();
  }

  public void clearHistory() {
    myHistories.clear();
  }

  private DebuggerTree getTree() {
    return myDebuggerTree;
  }
}
