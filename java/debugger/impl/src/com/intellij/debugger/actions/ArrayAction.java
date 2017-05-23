/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.settings.ArrayRendererConfigurable;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.evaluate.XExpressionDialog;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ArrayAction extends DebuggerAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());

    DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if(debugProcess == null) {
      return;
    }

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
    ArrayRenderer newRenderer = createNewRenderer(renderer, debuggerContext, node.getName());
    if (newRenderer != null) {
      debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
          final Renderer lastRenderer = descriptor.getLastRenderer();
          if (lastRenderer instanceof ArrayRenderer) {
            ((JavaValue)container).setRenderer(newRenderer, node);
            node.invokeNodeUpdate(() -> node.getTree().expandPath(node.getPath()));
          }
          else if (lastRenderer instanceof CompoundNodeRenderer) {
            final CompoundNodeRenderer compoundRenderer = (CompoundNodeRenderer)lastRenderer;
            final ChildrenRenderer childrenRenderer = compoundRenderer.getChildrenRenderer();
            if (childrenRenderer instanceof ExpressionChildrenRenderer) {
              ExpressionChildrenRenderer.setPreferableChildrenRenderer(descriptor, newRenderer);
              ((JavaValue)container).reBuild(node);
            }
          }
        }
      });
    }
  }

  @Nullable
  protected abstract ArrayRenderer createNewRenderer(ArrayRenderer original, @NotNull DebuggerContextImpl debuggerContext, String title);

  @Override
  public void update(AnActionEvent e) {
    boolean enable = false;
    List<JavaValue> values = ViewAsGroup.getSelectedValues(e);
    if (values.size() == 1) {
      enable = getArrayRenderer(values.get(0).getDescriptor()) != null;
    }
    e.getPresentation().setEnabledAndVisible(enable);
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

  private static class AdjustArrayRangeAction extends ArrayAction {
    @Override
    protected ArrayRenderer createNewRenderer(ArrayRenderer original, @NotNull DebuggerContextImpl debuggerContext, String title) {
      ArrayRenderer clonedRenderer = original.clone();
      clonedRenderer.setForced(true);
      if (ShowSettingsUtil.getInstance().editConfigurable(debuggerContext.getProject(), new NamedArrayConfigurable(title, clonedRenderer))) {
        return clonedRenderer;
      }
      return null;
    }
  }

  private static class FilterArrayAction extends ArrayAction {
    @Nullable
    @Override
    protected ArrayRenderer createNewRenderer(ArrayRenderer original, @NotNull DebuggerContextImpl debuggerContext, String title) {
      XExpressionDialog dialog =
        new XExpressionDialog(debuggerContext.getProject(),
                              debuggerContext.getDebugProcess().getXdebugProcess().getEditorsProvider(),
                              "filterExpression",
                              "Filter",
                              null,
                             original instanceof ArrayRenderer.Filtered
                               ? ((ArrayRenderer.Filtered)original).getExpression()
                               : XExpressionImpl.EMPTY_EXPRESSION);
      if (dialog.showAndGet()) {
        XExpression expression = dialog.getExpression();
        return XDebuggerUtilImpl.isEmptyExpression(expression) ?
                 NodeRendererSettings.getInstance().getArrayRenderer() :
                 new ArrayRenderer.Filtered(expression);
      }
      return null;
    }
  }
}
