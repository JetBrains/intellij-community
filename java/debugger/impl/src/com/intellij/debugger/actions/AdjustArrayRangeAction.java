/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.settings.ArrayRendererConfigurable;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

public class AdjustArrayRangeAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    if(debuggerContext == null) {
      return;
    }

    DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if(debugProcess == null) {
      return;
    }

    final Project project = debuggerContext.getProject();

    final DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if (selectedNode == null) {
      return;
    }
    NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
    if(!(descriptor instanceof ValueDescriptorImpl /*&& ((ValueDescriptorImpl)descriptor).isArray()*/)) {
      return;
    }

    final ArrayRenderer renderer = getArrayRenderer((ValueDescriptorImpl)descriptor)/*(ArrayRenderer)((ValueDescriptorImpl)selectedNode.getDescriptor()).getLastRenderer()*/;
    if (renderer == null) {
      return;
    }

    String title = createNodeTitle("", selectedNode);
    String label = selectedNode.toString();
    int index = label.indexOf('=');
    if (index > 0) {
      title = title + " " + label.substring(index);
    }
    final ArrayRenderer clonedRenderer = renderer.clone();
    final NamedArrayConfigurable configurable = new NamedArrayConfigurable(title, clonedRenderer);
    SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable,
                                                                   ShowSettingsUtilImpl.createDimensionKey(configurable), false);
    editor.show();

    if(editor.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
          public void contextAction() throws Exception {
            final ValueDescriptorImpl nodeDescriptor = (ValueDescriptorImpl)selectedNode.getDescriptor();
            final Renderer lastRenderer = nodeDescriptor.getLastRenderer();
            if (lastRenderer instanceof ArrayRenderer) {
              selectedNode.setRenderer(clonedRenderer);
            }
            else if (lastRenderer instanceof CompoundNodeRenderer) {
              final CompoundNodeRenderer compoundRenderer = (CompoundNodeRenderer)lastRenderer;
              final ChildrenRenderer childrenRenderer = compoundRenderer.getChildrenRenderer();
              if (childrenRenderer instanceof ExpressionChildrenRenderer) {
                ExpressionChildrenRenderer.setPreferableChildrenRenderer(nodeDescriptor, clonedRenderer);
                selectedNode.calcRepresentation();
              }
            }
          }
        });
    }
  }

  public void update(AnActionEvent e) {
    boolean enable = false;
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(selectedNode != null) {
      NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
      enable = descriptor instanceof ValueDescriptorImpl && getArrayRenderer((ValueDescriptorImpl)descriptor) != null;
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

    public String getDisplayName() {
      return myTitle;
    }

    public String getHelpTopic() {
      return null;
    }
  }
}
