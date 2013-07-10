/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Pair;
import com.sun.jdi.Value;

import java.util.*;

public abstract class DescriptorTestCase extends DebuggerTestCase {
  private final List<Pair<NodeDescriptorImpl,List<String>>> myDescriptorLog = new ArrayList<Pair<NodeDescriptorImpl, List<String>>>();

  public DescriptorTestCase() {
    super();
  }

  protected NodeRenderer getToStringRenderer() {
    return NodeRendererSettings.getInstance().getToStringRenderer();
  }

  protected NodeRenderer getMapRenderer() {
    return getAlternateCollectionRenderer("Map");
  }

  private NodeRenderer getAlternateCollectionRenderer(final String name) {
    final NodeRenderer[] renderers = NodeRendererSettings.getInstance().getAlternateCollectionRenderers();
    for (int idx = 0; idx < renderers.length; idx++) {
      NodeRenderer renderer = renderers[idx];
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
      ArrayList<String> allText = new ArrayList<String>();
      allText.add(text);
      descriptorText = new Pair<NodeDescriptorImpl, List<String>>(descriptor, allText);
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
    for (Iterator<Pair<NodeDescriptorImpl, List<String>>> iterator = myDescriptorLog.iterator(); iterator.hasNext();) {
      Pair<NodeDescriptorImpl, List<String>> pair = iterator.next();
      if(pair.getFirst() == descriptor) {
        descriptorText = pair;
        break;
      }
    }
    return descriptorText;
  }

  protected void flushDescriptor(final NodeDescriptorImpl descriptor) {
    Pair<NodeDescriptorImpl, List<String>> descriptorLog = findDescriptorLog(descriptor);
    if(descriptorLog != null) {
      printDescriptorLog(descriptorLog);
      myDescriptorLog.remove(descriptorLog);
    }
  }

  protected void flushDescriptors() {
    for (Iterator<Pair<NodeDescriptorImpl, List<String>>> iterator = myDescriptorLog.iterator(); iterator.hasNext();) {
      printDescriptorLog(iterator.next());
    }
    myDescriptorLog.clear();
  }

  private void printDescriptorLog(Pair<NodeDescriptorImpl, List<String>> pair) {
    for (Iterator<String> it = pair.getSecond().iterator(); it.hasNext();) {
      String text =  it.next();
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
    flushDescriptors();
    super.tearDown();
  }

  protected void expandAll(final DebuggerTree tree, final Runnable runnable) {
    doExpandAll(tree, runnable, new HashSet<Value>(), null);
  }

  protected static interface NodeFilter {
    boolean shouldExpand(DebuggerTreeNode node);
  }
  
  protected void expandAll(final DebuggerTree tree, final Runnable runnable, NodeFilter filter) {
    doExpandAll(tree, runnable, new HashSet<Value>(), filter);
  }

  private void doExpandAll(final DebuggerTree tree, final Runnable runnable, final Set<Value> alreadyExpanded, final NodeFilter filter) {
    invokeRatherLater(tree.getDebuggerContext().getSuspendContext(), new Runnable() {
      @Override
      public void run() {
        boolean anyCollapsed = false;
        for(int i = 0; i < tree.getRowCount(); i++) {
          final DebuggerTreeNode treeNode = (DebuggerTreeNode)tree.getPathForRow(i).getLastPathComponent();
          if(tree.isCollapsed(i) && !treeNode.isLeaf()) {
            final NodeDescriptor nodeDescriptor = treeNode.getDescriptor();
            boolean shouldExpand = filter == null? true : filter.shouldExpand(treeNode);
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
          doExpandAll(tree, runnable, alreadyExpanded, filter);
        }
        else {
          runnable.run();
        }
      }
    });
  }
}
