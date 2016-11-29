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
package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerTestCase;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.treeStructure.Tree;
import com.sun.jdi.Value;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class DescriptorTestCase extends DebuggerTestCase {
  private final List<Pair<NodeDescriptorImpl,List<String>>> myDescriptorLog = new ArrayList<>();

  public DescriptorTestCase() {
    super();
  }

  protected NodeRenderer getToStringRenderer() {
    return NodeRendererSettings.getInstance().getToStringRenderer();
  }

  protected NodeRenderer getMapRenderer() {
    return getAlternateCollectionRenderer("Map");
  }

  private static NodeRenderer getAlternateCollectionRenderer(final String name) {
    final NodeRenderer[] renderers = NodeRendererSettings.getInstance().getAlternateCollectionRenderers();
    for (NodeRenderer renderer : renderers) {
      if (name.equals(renderer.getName())) {
        return renderer;
      }
    }
    return null;
  }

  protected NodeRenderer getMapEntryRenderer() {
    return getAlternateCollectionRenderer("Map.Entry");
  }

  protected NodeRenderer getHexRenderer() {
    return NodeRendererSettings.getInstance().getHexRenderer();
  }

  protected NodeRenderer getCollectionRenderer() {
    return getAlternateCollectionRenderer("Collection");
  }

  @Override
  protected void resume(final SuspendContextImpl suspendContext) {
    final DebugProcessImpl localProcess = suspendContext.getDebugProcess();
    invokeRatherLater(new SuspendContextCommandImpl(suspendContext) {
      @Override
      public void contextAction() throws Exception {
        flushDescriptors();
        localProcess.getManagerThread().schedule(localProcess.createResumeCommand(suspendContext, Priority.LOW));
      }
    });
  }

  protected void logDescriptor(final NodeDescriptorImpl descriptor, String text) {
    Pair<NodeDescriptorImpl, List<String>> descriptorText = findDescriptorLog(descriptor);

    if(descriptorText == null) {
      ArrayList<String> allText = new ArrayList<>();
      allText.add(text);
      descriptorText = new Pair<>(descriptor, allText);
      myDescriptorLog.add(descriptorText);
    }
    else {
      List<String> allText = descriptorText.getSecond();
      if(!allText.get(allText.size() - 1).equals(text)) {
        allText.add(text);
      }
    }
  }

  private Pair<NodeDescriptorImpl, List<String>> findDescriptorLog(final NodeDescriptorImpl descriptor) {
    Pair<NodeDescriptorImpl, List<String>> descriptorText = null;
    for (Pair<NodeDescriptorImpl, List<String>> pair : myDescriptorLog) {
      if (pair.getFirst() == descriptor) {
        descriptorText = pair;
        break;
      }
    }
    return descriptorText;
  }

  protected void flushDescriptors() {
    for (Pair<NodeDescriptorImpl, List<String>> aMyDescriptorLog : myDescriptorLog) {
      printDescriptorLog(aMyDescriptorLog);
    }
    myDescriptorLog.clear();
  }

  private void printDescriptorLog(Pair<NodeDescriptorImpl, List<String>> pair) {
    for (String text : pair.getSecond()) {
      print(text, ProcessOutputTypes.SYSTEM);
    }
  }

  protected void disableRenderer(NodeRenderer renderer) {
    renderer.setEnabled(false);
  }

  protected void enableRenderer(NodeRenderer renderer) {
    renderer.setEnabled(true);
  }

  protected LocalVariableDescriptorImpl localVar(DebuggerTree frameTree,
                                               EvaluationContextImpl evaluationContext,
                                               String name) {
    try {
      StackFrameProxy frameProxy = evaluationContext.getFrameProxy();
      assert frameProxy != null;
      LocalVariableDescriptorImpl local = frameTree.getNodeFactory().getLocalVariableDescriptor(null, frameProxy.visibleVariableByName(name));
      local.setContext(evaluationContext);
      return local;
    } catch (EvaluateException e) {
      error(e);
      return null;
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      flushDescriptors();
    }
    finally {
      super.tearDown();
    }
  }

  protected void expandAll(final DebuggerTree tree, final Runnable runnable) {
    expandAll(tree, runnable, new HashSet<>(), null);
  }

  protected interface NodeFilter {
    boolean shouldExpand(TreeNode node);
  }

  protected void expandAll(final DebuggerTree tree, final Runnable runnable, final Set<Value> alreadyExpanded, final NodeFilter filter) {
    expandAll(tree, runnable, alreadyExpanded, filter, tree.getDebuggerContext().getSuspendContext());
  }

  protected void expandAll(final Tree tree,
                           final Runnable runnable,
                           final Set<Value> alreadyExpanded,
                           final NodeFilter filter,
                           final SuspendContextImpl context) {
    invokeRatherLater(context, () -> {
      boolean anyCollapsed = false;
      for(int i = 0; i < tree.getRowCount(); i++) {
        final TreeNode treeNode = (TreeNode)tree.getPathForRow(i).getLastPathComponent();
        if(tree.isCollapsed(i) && !treeNode.isLeaf()) {
          NodeDescriptor nodeDescriptor = null;
          if (treeNode instanceof DebuggerTreeNodeImpl) {
            nodeDescriptor = ((DebuggerTreeNodeImpl)treeNode).getDescriptor();
          }
          boolean shouldExpand = filter == null || filter.shouldExpand(treeNode);
          if (shouldExpand) {
            // additional checks to prevent infinite expand
            if (nodeDescriptor instanceof ValueDescriptor) {
              final Value value = ((ValueDescriptor)nodeDescriptor).getValue();
              shouldExpand = !alreadyExpanded.contains(value);
              if (shouldExpand) {
                alreadyExpanded.add(value);
              }
            }
          }
          if (shouldExpand) {
            anyCollapsed = true;
            tree.expandRow(i);
          }
        }
      }

      if (anyCollapsed) {
        expandAll(tree, runnable, alreadyExpanded, filter, context);
      }
      else {
        runnable.run();
      }
    });
  }
}
