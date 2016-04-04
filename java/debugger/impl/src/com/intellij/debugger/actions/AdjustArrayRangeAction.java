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
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.settings.ArrayRendererConfigurable;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.Nullable;

public class AdjustArrayRangeAction extends DebuggerAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());

    DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if(debugProcess == null) {
      return;
    }

    final Project project = debuggerContext.getProject();

    final XValueNodeImpl node = XDebuggerTreeActionBase.getSelectedNode(e.getDataContext());
    if (node == null) {
      return;
    }

    final XValue container = node.getValueContainer();
    if (!(container instanceof JavaValue)) {
      return;
    }

    final ValueDescriptorImpl descriptor = ((JavaValue)container).getDescriptor();
    ArrayRenderer renderer = getArrayRenderer(descriptor);
    if (renderer == null) {
      return;
    }

    //String title = createNodeTitle("", selectedNode);
    //String label = selectedNode.toString();
    //int index = label.indexOf('=');
    //if (index > 0) {
    //  title = title + " " + label.substring(index);
    //}
    String title = node.getName();
    final ArrayRenderer clonedRenderer = renderer.clone();
    clonedRenderer.setForced(true);
    if (ShowSettingsUtil.getInstance().editConfigurable(project, new NamedArrayConfigurable(title, clonedRenderer))) {
      debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
        @Override
        public void contextAction() throws Exception {
          final Renderer lastRenderer = descriptor.getLastRenderer();
          if (lastRenderer instanceof ArrayRenderer) {
            ((JavaValue)container).setRenderer(clonedRenderer, node);
          }
          else if (lastRenderer instanceof CompoundNodeRenderer) {
            final CompoundNodeRenderer compoundRenderer = (CompoundNodeRenderer)lastRenderer;
            final ChildrenRenderer childrenRenderer = compoundRenderer.getChildrenRenderer();
            if (childrenRenderer instanceof ExpressionChildrenRenderer) {
              ExpressionChildrenRenderer.setPreferableChildrenRenderer(descriptor, clonedRenderer);
              ((JavaValue)container).reBuild(node);
            }
          }
        }
      });
    }
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enable = false;
    XValueNodeImpl node = XDebuggerTreeActionBase.getSelectedNode(e.getDataContext());
    if (node != null) {
      XValue container = node.getValueContainer();
      if (container instanceof JavaValue) {
        ValueDescriptorImpl descriptor = ((JavaValue)container).getDescriptor();
        enable = getArrayRenderer(descriptor) != null;
      }
    }
    e.getPresentation().setVisible(enable);
  }

  @Nullable
  private static ArrayRenderer getArrayRenderer(ValueDescriptorImpl descriptor) {
    final Renderer lastRenderer = descriptor.getLastRenderer();
    if (lastRenderer instanceof ArrayRenderer) {
      return (ArrayRenderer)lastRenderer;
    }
    if (lastRenderer instanceof CompoundNodeRenderer && ((CompoundNodeRenderer)lastRenderer).getChildrenRenderer() instanceof ExpressionChildrenRenderer) {
      final NodeRenderer lastChildrenRenderer = ExpressionChildrenRenderer.getLastChildrenRenderer(descriptor);
      if (lastChildrenRenderer instanceof ArrayRenderer) {
        return (ArrayRenderer)lastChildrenRenderer;
      }
    }
    return null;
  }

  private static String createNodeTitle(String prefix, DebuggerTreeNodeImpl node) {
    if (node != null) {
      DebuggerTreeNodeImpl parent = node.getParent();
      NodeDescriptorImpl descriptor = parent.getDescriptor();
      if (descriptor instanceof ValueDescriptorImpl && ((ValueDescriptorImpl)descriptor).isArray()) {
        int index = parent.getIndex(node);
        return createNodeTitle(prefix, parent) + "[" + index + "]";
      }
      String name = (node.getDescriptor() != null)? node.getDescriptor().getName() : null;
      return (name != null)? prefix + " " + name : prefix;
    }
    return prefix;
  }

  private static class NamedArrayConfigurable extends ArrayRendererConfigurable implements Configurable {
    private final String myTitle;

    public NamedArrayConfigurable(String title, ArrayRenderer renderer) {
      super(renderer);
      myTitle = title;
    }

    @Override
    public String getDisplayName() {
      return myTitle;
    }

    @Override
    public String getHelpTopic() {
      return null;
    }
  }
}
