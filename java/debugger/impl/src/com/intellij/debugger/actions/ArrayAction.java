// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.settings.ArrayRendererConfigurable;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.render.*;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxyKeeperKt;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.List;

public abstract class ArrayAction extends DebuggerAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());

    DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if (debugProcess == null) {
      return;
    }

    final XValueNodeImpl node = XDebuggerTreeActionBase.getSelectedNode(e.getDataContext());
    if (node == null) {
      return;
    }

    XDebugSessionProxy sessionProxy = DebuggerUIUtil.getSessionProxy(e);
    if (sessionProxy == null) {
      return;
    }

    ArrayRenderer renderer = getArrayRenderer(node.getValueContainer(), sessionProxy);
    if (renderer == null) {
      return;
    }

    //String title = createNodeTitle("", selectedNode);
    //String label = selectedNode.toString();
    //int index = label.indexOf('=');
    //if (index > 0) {
    //  title = title + " " + label.substring(index);
    //}
    createNewRenderer(node, renderer, debuggerContext, node.getName())
      .onSuccess(newRenderer -> setArrayRenderer(newRenderer, node, debuggerContext));
  }

  protected abstract @NotNull Promise<ArrayRenderer> createNewRenderer(XValueNodeImpl node,
                                                                       ArrayRenderer original,
                                                                       @NotNull DebuggerContextImpl debuggerContext,
                                                                       String title);

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enable = false;
    List<JavaValue> values = ViewAsGroup.getSelectedValues(e);
    XDebugSessionProxy sessionProxy = DebuggerUIUtil.getSessionProxy(e);
    if (values.size() == 1 && sessionProxy != null) {
      enable = getArrayRenderer(values.get(0), sessionProxy) != null;
    }
    e.getPresentation().setEnabledAndVisible(enable);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public static @Nullable ArrayRenderer getArrayRenderer(XValue value, XDebugSessionProxy sessionProxy) {
    JavaValue javaValue = MonolithJavaValueUtilsKt.findJavaValue(value, sessionProxy);
    if (javaValue != null) {
      ValueDescriptorImpl descriptor = javaValue.getDescriptor();
      Renderer lastRenderer = descriptor.getLastRenderer();
      if (lastRenderer instanceof CompoundReferenceRenderer compoundReferenceRenderer) {
        ChildrenRenderer childrenRenderer = compoundReferenceRenderer.getChildrenRenderer();
        if (childrenRenderer instanceof ExpressionChildrenRenderer expressionChildrenRenderer) {
          lastRenderer = ExpressionChildrenRenderer.getLastChildrenRenderer(descriptor);
          if (lastRenderer == null) {
            lastRenderer = expressionChildrenRenderer.getPredictedRenderer();
          }
        }
      }
      if (lastRenderer instanceof ArrayRenderer arrayRenderer) {
        return arrayRenderer;
      }
    }
    return null;
  }

  public static void setArrayRenderer(ArrayRenderer newRenderer, @NotNull XValueNodeImpl node, @NotNull DebuggerContextImpl debuggerContext) {
    XDebugSessionProxy sessionProxy = findProxyFromContext(debuggerContext);
    if (sessionProxy == null) return;

    XValue container = node.getValueContainer();

    ArrayRenderer renderer = getArrayRenderer(container, sessionProxy);
    if (renderer == null) {
      return;
    }

    JavaValue javaValue = MonolithJavaValueUtilsKt.findJavaValue(container, sessionProxy);
    assert javaValue != null;
    ValueDescriptorImpl descriptor = javaValue.getDescriptor();

    DebuggerManagerThreadImpl managerThread = debuggerContext.getManagerThread();
    if (managerThread != null) {
      managerThread.schedule(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) {
          final Renderer lastRenderer = descriptor.getLastRenderer();
          if (lastRenderer instanceof ArrayRenderer) {
            javaValue.setRenderer(newRenderer, node);
            node.invokeNodeUpdate(() -> node.getTree().expandPath(node.getPath()));
          }
          else if (lastRenderer instanceof CompoundReferenceRenderer compoundRenderer) {
            final ChildrenRenderer childrenRenderer = compoundRenderer.getChildrenRenderer();
            if (childrenRenderer instanceof ExpressionChildrenRenderer) {
              ExpressionChildrenRenderer.setPreferableChildrenRenderer(descriptor, newRenderer);
              javaValue.reBuild(node);
            }
          }
        }
      });
    }
  }

  private static @Nullable XDebugSessionProxy findProxyFromContext(@NotNull DebuggerContextImpl debuggerContext) {
    DebuggerSession javaSession = debuggerContext.getDebuggerSession();
    if (javaSession == null) return null;
    XDebugSession debugSession = javaSession.getXDebugSession();
    if (debugSession == null) return null;
    return XDebugSessionProxyKeeperKt.asProxy(debugSession);
  }

  private static String createNodeTitle(String prefix, DebuggerTreeNodeImpl node) {
    if (node != null) {
      DebuggerTreeNodeImpl parent = node.getParent();
      NodeDescriptorImpl descriptor = parent.getDescriptor();
      if (descriptor instanceof ValueDescriptorImpl && ((ValueDescriptorImpl)descriptor).isArray()) {
        int index = parent.getIndex(node);
        return createNodeTitle(prefix, parent) + "[" + index + "]";
      }
      String name = (node.getDescriptor() != null) ? node.getDescriptor().getName() : null;
      return (name != null) ? prefix + " " + name : prefix;
    }
    return prefix;
  }

  private static class NamedArrayConfigurable extends ArrayRendererConfigurable implements Configurable {
    private final @NlsContexts.ConfigurableName String myTitle;

    NamedArrayConfigurable(@NlsContexts.ConfigurableName String title, ArrayRenderer renderer) {
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

  public static class AdjustArrayRangeAction extends ArrayAction {
    @Override
    protected @NotNull Promise<ArrayRenderer> createNewRenderer(XValueNodeImpl node,
                                                                ArrayRenderer original,
                                                                @NotNull DebuggerContextImpl debuggerContext,
                                                                @NlsContexts.ConfigurableName String title) {
      ArrayRenderer clonedRenderer = original.clone();
      clonedRenderer.setForced(true);
      if (ShowSettingsUtil.getInstance().editConfigurable(debuggerContext.getProject(), new NamedArrayConfigurable(title, clonedRenderer))) {
        return Promises.resolvedPromise(clonedRenderer);
      }
      return Promises.rejectedPromise();
    }
  }

  public static class FilterArrayAction extends ArrayAction {
    @Override
    protected @NotNull Promise<ArrayRenderer> createNewRenderer(XValueNodeImpl node,
                                                                ArrayRenderer original,
                                                                @NotNull DebuggerContextImpl debuggerContext,
                                                                String title) {
      XDebugSessionProxy sessionProxy = findProxyFromContext(debuggerContext);
      if (sessionProxy == null) return Promises.rejectedPromise();
      ArrayFilterInplaceEditor.editParent(node, sessionProxy);
      return Promises.rejectedPromise();
    }
  }
}
