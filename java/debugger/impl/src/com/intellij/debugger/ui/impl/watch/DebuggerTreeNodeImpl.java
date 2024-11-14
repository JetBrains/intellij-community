// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class DebuggerTreeNodeImpl
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.DebuggerTreeRenderer;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.SimpleColoredText;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.MutableTreeNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DebuggerTreeNodeImpl extends TreeBuilderNode implements DebuggerTreeNode, NodeDescriptorProvider, MutableTreeNode {
  private Icon myIcon;
  private SimpleColoredText myText;
  private final DebuggerTree myTree;
  private final Map myProperties = new HashMap();

  public DebuggerTreeNodeImpl(DebuggerTree tree, NodeDescriptor descriptor) {
    super(descriptor);
    myTree = tree;
  }

  @Override
  public DebuggerTreeNodeImpl getParent() {
    return (DebuggerTreeNodeImpl)super.getParent();
  }

  @Override
  protected TreeBuilder getTreeBuilder() {
    return myTree.getMutableModel();
  }

  public DebuggerTree getTree() {
    return myTree;
  }

  public String toString() {
    return myText != null ? myText.toString() : "";
  }

  @Override
  public NodeDescriptorImpl getDescriptor() {
    return (NodeDescriptorImpl)getUserObject();
  }

  @Override
  public Project getProject() {
    return getTree().getProject();
  }

  @Override
  public void setRenderer(NodeRenderer renderer) {
    ((ValueDescriptorImpl)getDescriptor()).setRenderer(renderer);
    calcRepresentation();
  }

  private void updateCaches() {
    final NodeDescriptorImpl descriptor = getDescriptor();
    myIcon = DebuggerTreeRenderer.getDescriptorIcon(descriptor);
    final DebuggerContextImpl context = getTree().getDebuggerContext();
    myText = DebuggerTreeRenderer.getDescriptorText(context, descriptor, DebuggerUIUtil.getColorScheme(myTree), false);
  }

  public Icon getIcon() {
    return myIcon;
  }

  public SimpleColoredText getText() {
    return myText;
  }

  @Override
  public void clear() {
    removeAllChildren();
    myIcon = null;
    myText = null;
    super.clear();
  }

  private void update(final DebuggerContextImpl context, final Runnable runnable, boolean labelOnly) {
    if (!labelOnly) {
      clear();
    }

    if (context != null && context.getDebugProcess() != null) {
      getTree().saveState(this);

      myIcon = DebuggerTreeRenderer.getDescriptorIcon(MessageDescriptor.EVALUATING);
      myText = DebuggerTreeRenderer.getDescriptorText(context, MessageDescriptor.EVALUATING, false);

      Objects.requireNonNull(context.getManagerThread()).invoke(new DebuggerContextCommandImpl(context) {
        @Override
        public void threadAction(@NotNull SuspendContextImpl suspendContext) {
          runnable.run();
        }

        @Override
        protected void commandCancelled() {
          clear();
          getDescriptor().clear();
          updateCaches();

          labelChanged();
          childrenChanged(true);
        }

        @Override
        public Priority getPriority() {
          return Priority.NORMAL;
        }
      });
    }

    labelChanged();
    if (!labelOnly) {
      childrenChanged(true);
    }
  }

  public void calcLabel() {
    final DebuggerContextImpl context = getTree().getDebuggerContext();
    update(context, () -> getDescriptor().updateRepresentation(context.createEvaluationContext(), new DescriptorLabelListener() {
      @Override
      public void labelChanged() {
        updateCaches();
        DebuggerTreeNodeImpl.this.labelChanged();
      }
    }), true);
  }

  public void calcRepresentation() {
    final DebuggerContextImpl context = getTree().getDebuggerContext();
    update(context, () -> getDescriptor().updateRepresentation(context.createEvaluationContext(), new DescriptorLabelListener() {
      @Override
      public void labelChanged() {
        updateCaches();
        DebuggerTreeNodeImpl.this.labelChanged();
      }
    }), false);
  }

  public void calcValue() {
    final DebuggerContextImpl context = getTree().getDebuggerContext();
    update(
      context,
      () -> {
        EvaluationContextImpl evaluationContext = context.createEvaluationContext();
        getDescriptor().setContext(evaluationContext);
        getDescriptor().updateRepresentation(evaluationContext, new DescriptorLabelListener() {
          @Override
          public void labelChanged() {
            updateCaches();
            DebuggerTreeNodeImpl.this.labelChanged();
          }
        });
        childrenChanged(true);
      }, false);
  }

  private static void invoke(Runnable r) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      r.run();
    }
    else {
      SwingUtilities.invokeLater(r);
    }
  }

  public void labelChanged() {
    invoke(() -> {
      updateCaches();
      getTree().getMutableModel().nodeChanged(this);
    });
  }

  public void childrenChanged(final boolean scrollToVisible) {
    invoke(() -> {
      getTree().getMutableModel().nodeStructureChanged(this);
      getTree().restoreState(this);
    });
  }

  public DebuggerTreeNodeImpl add(MessageDescriptor message) {
    DebuggerTreeNodeImpl node = getNodeFactory().createMessageNode(message);
    add(node);
    return node;
  }

  public NodeManagerImpl getNodeFactory() {
    return myTree.getNodeFactory();
  }

  public Object getProperty(Key key) {
    return myProperties.get(key);
  }

  public void putProperty(Key key, Object data) {
    myProperties.put(key, data);
  }

  @NotNull
  public static DebuggerTreeNodeImpl createNodeNoUpdate(DebuggerTree tree, NodeDescriptor descriptor) {
    DebuggerTreeNodeImpl node = new DebuggerTreeNodeImpl(tree, descriptor);
    node.updateCaches();
    return node;
  }

  @NotNull
  protected static DebuggerTreeNodeImpl createNode(DebuggerTree tree, NodeDescriptorImpl descriptor, EvaluationContextImpl evaluationContext) {
    final DebuggerTreeNodeImpl node = new DebuggerTreeNodeImpl(tree, descriptor);
    descriptor.updateRepresentationNoNotify(evaluationContext, new DescriptorLabelListener() {
      @Override
      public void labelChanged() {
        node.updateCaches();
        node.labelChanged();
      }
    });
    node.updateCaches();
    return node;
  }
}
