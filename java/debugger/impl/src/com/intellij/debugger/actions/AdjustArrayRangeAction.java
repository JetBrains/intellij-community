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
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.settings.ArrayRendererConfigurable;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.debugger.ui.tree.render.Renderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.UIUtil;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class AdjustArrayRangeAction extends DebuggerAction {

  private static final AdjustedRendererManager[] MANAGERS = { new ArrayRendererManager(), new ListRendererManager() };
  
  public void actionPerformed(AnActionEvent e) {
    final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    if(debuggerContext == null) {
      return;
    }

    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if(debugProcess == null) {
      return;
    }

    final DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if (selectedNode == null) {
      return;
    }

    debugProcess.getManagerThread().invoke(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        adjustRange(debuggerContext, selectedNode, debugProcess);
      }
    });
  }

  private static void adjustRange(@NotNull final DebuggerContextImpl debuggerContext, @NotNull final DebuggerTreeNodeImpl selectedNode,
                                  @NotNull final DebugProcessImpl debugProcess)
  {
    final Project project = debuggerContext.getProject();
    final ValueDescriptorImpl valueDescriptor = (ValueDescriptorImpl)selectedNode.getDescriptor();
    ArrayRenderer renderer = null;
    AdjustedRendererManager manager = null;
    for (AdjustedRendererManager m : MANAGERS) {
      renderer = m.getRenderer(selectedNode, debuggerContext);
      if (renderer != null) {
        manager = m;
        break;
      } 
    }
    if (renderer == null) {
      return;
    }

    String title = createNodeTitle("", selectedNode);
    String label = selectedNode.toString();
    int index = label.indexOf('=');
    if (index > 0) {
      title = title + " " + label.substring(index);
    }
    final ArrayRenderer cloneRenderer = renderer.clone();
    final AdjustedRendererManager m = manager;

    final String finalTitle = title;
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        SingleConfigurableEditor editor = new SingleConfigurableEditor(project, new NamedArrayConfigurable(finalTitle, cloneRenderer)) {
          protected Action[] createActions() {
            final String helpTopic = getConfigurable().getHelpTopic();
            return (helpTopic != null) ?
                   new Action[]{getOKAction(), getCancelAction(), getHelpAction()} :
                   new Action[]{getOKAction(), getCancelAction()};
          }
        };
        editor.show();
        if(editor.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
              public void contextAction() throws Exception {
                m.apply(cloneRenderer, valueDescriptor, debuggerContext);
                selectedNode.clear();
                selectedNode.calcRepresentation();
              }
          });
        }
      }
    });

  }

  public void update(AnActionEvent e) {
    boolean enable = false;
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(selectedNode != null) {
      final NodeDescriptorImpl d = selectedNode.getDescriptor();
      if (d instanceof ValueDescriptorImpl) {
        ValueDescriptorImpl descriptor = (ValueDescriptorImpl)d;
        for (AdjustedRendererManager manager : MANAGERS) {
          if (manager.canBeApplied(descriptor)) {
            enable = true;
            break;
          }
        }
      } 
    }
    e.getPresentation().setVisible(enable);
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

    public Icon getIcon() {
      return null;
    }

    public String getHelpTopic() {
      return null;
    }
  }

  /**
   * Encapsulates details of array renderer retrieval and appliance (e.g. different logic is used for array references and list references).
   * <p/>
   * Implementations of this interface are assumed to be thread-safe.
   */
  private interface AdjustedRendererManager {

    /**
     * Allows to answer if current action can be applied to the value referenced by the given descriptor.
     * <p/>
     * <b>Note:</b> we can't just use checking like <code>'{@link #getRenderer(DebuggerTreeNode, DebuggerContextImpl)} == null'</code>
     * because this method is called outside of debug manager thread context, i.e. it's assumed to be called on
     * {@link AnAction#update(AnActionEvent)} and perform light-weight check from EDT.
     * 
     * @param descriptor  target value descriptor
     * @return            <code>true</code> if current action can be applied to the value referenced by the given descriptor;
     *                    <code>false</code> otherwise
     */
    boolean canBeApplied(@NotNull ValueDescriptorImpl descriptor);
    
    /**
     * @param node        target node
     * @param context     current debug context process
     * @return            array-like data renderer used at the moment (if any)
     */
    @Nullable
    ArrayRenderer getRenderer(@NotNull DebuggerTreeNode node, @NotNull DebuggerContextImpl context);

    /**
     * Asks to use given renderer for array-like data.
     *
     * @param renderer      renderer to use
     * @param descriptor    target node descriptor
     * @param context       target debugging context
     */
    void apply(@NotNull ArrayRenderer renderer, @NotNull ValueDescriptorImpl descriptor, @NotNull DebuggerContextImpl context);
  }
  
  private static abstract class AbstractAdjustedRendererManager implements AdjustedRendererManager {
    
    @Override
    public ArrayRenderer getRenderer(@NotNull final DebuggerTreeNode node, @NotNull final DebuggerContextImpl context) {
      final NodeDescriptor descriptor = node.getDescriptor();
      if (!(descriptor instanceof ValueDescriptorImpl)) {
        return null;
      }

      return getRenderer(node, (ValueDescriptorImpl)descriptor, context);
    }
    
    @Nullable
    protected abstract ArrayRenderer getRenderer(@NotNull DebuggerTreeNode node, @NotNull ValueDescriptorImpl descriptor,
                                                 @NotNull DebuggerContextImpl context);
  }
  
  private static class ArrayRendererManager extends AbstractAdjustedRendererManager {

    @Override
    public boolean canBeApplied(@NotNull ValueDescriptorImpl descriptor) {
      return descriptor.getLastRenderer() instanceof ArrayRenderer;
    }

    @Override
    public ArrayRenderer getRenderer(@NotNull DebuggerTreeNode node, @NotNull ValueDescriptorImpl descriptor,
                                     @NotNull DebuggerContextImpl context)
    {
      final Renderer result = descriptor.getLastRenderer();
      if (result instanceof ArrayRenderer) {
        return (ArrayRenderer)result;
      }
      return null;
    }

    @Override
    public void apply(@NotNull ArrayRenderer renderer, @NotNull ValueDescriptorImpl valueDescriptor, @NotNull DebuggerContextImpl context) {
      valueDescriptor.setRenderer(renderer, context.getDebugProcess(), true);
    }
  }
  
  private static class ListRendererManager extends AbstractAdjustedRendererManager {

    @Override
    public boolean canBeApplied(@NotNull ValueDescriptorImpl descriptor) {
      final Value value = descriptor.getValue();
      if (value == null) {
        return false;
      }
      final Type type = value.type();
      if (type == null) {
        return false;
      }
      
      return DebuggerUtils.instanceOf(type, List.class.getName());
    }

    @Override
    public ArrayRenderer getRenderer(@NotNull DebuggerTreeNode node, @NotNull final ValueDescriptorImpl descriptor,
                                     @NotNull final DebuggerContextImpl context)
    {
      final DelegatingChildrenRenderer delegatingRenderer = getDelegateRenderer(descriptor, context.getDebugProcess());
      if (delegatingRenderer == null) {
        return null;
      }
      final ChildrenRenderer result = delegatingRenderer.getDelegate(context.createEvaluationContext(), descriptor, descriptor.getValue());
      if (result instanceof ArrayRenderer) {
        return (ArrayRenderer)result;
      }
      return null;
    }

    @Override
    public void apply(@NotNull ArrayRenderer renderer, @NotNull ValueDescriptorImpl descriptor, @NotNull DebuggerContextImpl context) {
      final DelegatingChildrenRenderer delegateRenderer = getDelegateRenderer(descriptor, context.getDebugProcess());
      if (delegateRenderer != null) {
        delegateRenderer.setDelegate(context.createEvaluationContext(), descriptor, descriptor.getValue(), renderer);
      } 
    }

    @Nullable
    private static DelegatingChildrenRenderer getDelegateRenderer(ValueDescriptorImpl descriptor, DebugProcessImpl process) {
      final NodeRenderer renderer = descriptor.getRenderer(process);
      if (!(renderer instanceof CompoundReferenceRenderer)) {
        return null;
      }

      CompoundReferenceRenderer compoundReferenceRenderer = (CompoundReferenceRenderer)renderer;
      final ChildrenRenderer result = compoundReferenceRenderer.getChildrenRenderer();
      if (result instanceof DelegatingChildrenRenderer) {
        return (DelegatingChildrenRenderer)result;
      }
      else {
        return null;
      } 
    }
  }
}
