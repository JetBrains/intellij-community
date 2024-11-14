// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerTestCase;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;
import java.util.*;

public abstract class DescriptorTestCase extends DebuggerTestCase {
  private final Map<NodeDescriptorImpl, NodeDescriptorText> myDescriptorLog = new LinkedHashMap<>();

  public DescriptorTestCase() {
    super();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    atDebuggerTearDown(() -> flushDescriptors());
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
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        flushDescriptors();
        suspendContext.getManagerThread().schedule(localProcess.createResumeCommand(suspendContext, Priority.LOW));
      }
    });
  }

  private class NodeDescriptorText {
    final List<String> myText = new SmartList<>();
    String myLabel;

    void appendText(String text) {
      if (!text.equals(ContainerUtil.getLastItem(myText))) {
        myText.add(text);
      }
    }

    void print() {
      for (String text : myText) {
        DescriptorTestCase.this.print(text, ProcessOutputTypes.SYSTEM);
      }
      if (StringUtil.isNotEmpty(myLabel)) {
        DescriptorTestCase.this.print(myLabel, ProcessOutputTypes.SYSTEM);
      }
    }
  }

  protected void logDescriptor(NodeDescriptorImpl descriptor, String text) {
    myDescriptorLog.computeIfAbsent(descriptor, k -> new NodeDescriptorText()).appendText(text);
  }

  protected void logDescriptorLabel(NodeDescriptorImpl descriptor, String label) {
    myDescriptorLog.computeIfAbsent(descriptor, k -> new NodeDescriptorText()).myLabel = label;
  }

  protected void flushDescriptors() {
    myDescriptorLog.forEach((descriptor, text) -> text.print());
    myDescriptorLog.clear();
  }

  private static boolean expandOne(Tree tree) {
    boolean anyExpanded = false;
    for (int i = 0; i < tree.getRowCount(); i++) {
      TreeNode treeNode = (TreeNode)tree.getPathForRow(i).getLastPathComponent();
      if (tree.isCollapsed(i) && !treeNode.isLeaf()) {
        anyExpanded = true;
        tree.expandRow(i);
        break;
      }
    }

    return anyExpanded;
  }

  protected static void expandAll(Tree tree, Runnable wait) {
    boolean cont = true;
    while (cont) {
      cont = UIUtil.invokeAndWaitIfNeeded(() -> expandOne(tree));
      wait.run();
    }
  }

  // Still used in kotlin tests
  protected void expandAll(final DebuggerTree tree, final Runnable runnable) {
    expandAll(tree, runnable, new HashSet<>(), null);
  }

  protected interface NodeFilter {
    boolean shouldExpand(TreeNode node);
  }

  protected void expandAll(final DebuggerTree tree,
                           final Runnable runnable,
                           final Set<? super Value> alreadyExpanded,
                           final NodeFilter filter) {
    expandAll(tree, runnable, alreadyExpanded, filter, tree.getDebuggerContext().getSuspendContext());
  }

  protected void expandAll(final Tree tree,
                           final Runnable runnable,
                           final Set<? super Value> alreadyExpanded,
                           final NodeFilter filter,
                           final SuspendContextImpl context) {
    invokeRatherLater(context, () -> {
      boolean anyCollapsed = false;
      for (int i = 0; i < tree.getRowCount(); i++) {
        final TreeNode treeNode = (TreeNode)tree.getPathForRow(i).getLastPathComponent();
        if (tree.isCollapsed(i) && !treeNode.isLeaf()) {
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
