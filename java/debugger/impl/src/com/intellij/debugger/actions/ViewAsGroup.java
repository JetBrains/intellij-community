// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ViewAsGroup extends ActionGroup implements DumbAware {
  private static final Logger LOG = Logger.getInstance(ViewAsGroup.class);

  private volatile AnAction[] myChildren = AnAction.EMPTY_ARRAY;

  public ViewAsGroup() {
    super(null, true);
  }

  private static class RendererAction extends ToggleAction {
    private final NodeRenderer myNodeRenderer;

    RendererAction(NodeRenderer nodeRenderer) {
      super(nodeRenderer.getName());
      myNodeRenderer = nodeRenderer;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      List<JavaValue> values = getSelectedValues(e);
      if (values.isEmpty()) {
        return false;
      }
      for (JavaValue value : values) {
        if (value.getDescriptor().getLastRenderer() != myNodeRenderer) {
          return false;
        }
      }
      return true;
    }

    @Override
    public void setSelected(@NotNull final AnActionEvent e, final boolean state) {
      if (!state) return;

      final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
      final List<JavaValue> values = getSelectedValues(e);
      final List<XValueNodeImpl> selectedNodes = XDebuggerTreeActionBase.getSelectedNodes(e.getDataContext());

      LOG.assertTrue(!values.isEmpty());

      DebugProcessImpl process = debuggerContext.getDebugProcess();
      if (process == null) {
        return;
      }

      process.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
        @Override
        public void threadAction(@NotNull SuspendContextImpl suspendContext) {
          for (XValueNodeImpl node : selectedNodes) {
            XValue container = node.getValueContainer();
            if (container instanceof JavaValue) {
              ((JavaValue)container).setRenderer(myNodeRenderer, node);
            }
          }
        }
      });
    }
  }

  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable final AnActionEvent e) {
    return myChildren;
  }

  private static AnAction [] calcChildren(List<JavaValue> values, Project project) {
    List<AnAction> renderers = new ArrayList<>();

    List<NodeRenderer> allRenderers = NodeRendererSettings.getInstance().getAllRenderers(project);

    boolean anyValueDescriptor = false;

    for (NodeRenderer nodeRenderer : allRenderers) {
      boolean allApp = true;

      for (JavaValue value : values) {
        if (value instanceof JavaReferringObjectsValue) { // disable for any referrers at all
          return AnAction.EMPTY_ARRAY;
        }
        ValueDescriptorImpl valueDescriptor = value.getDescriptor();
        anyValueDescriptor = true;
        if (!valueDescriptor.isValueValid() || !nodeRenderer.isApplicable(valueDescriptor.getType())) {
          allApp = false;
          break;
        }
      }

      if (!anyValueDescriptor) {
        return AnAction.EMPTY_ARRAY;
      }

      if (allApp) {
        renderers.add(new RendererAction(nodeRenderer));
      }
    }

    List<AnAction> children = new ArrayList<>();
    AnAction[] viewAsActions = ((DefaultActionGroup) ActionManager.getInstance().getAction(DebuggerActions.REPRESENTATION_LIST)).getChildren(null);
    for (AnAction viewAsAction : viewAsActions) {
      if (viewAsAction instanceof AutoRendererAction) {
        if (renderers.size() > 1) {
          viewAsAction.getTemplatePresentation().setVisible(true);
          children.add(viewAsAction);
        }
      }
      else {
        children.add(viewAsAction);
      }
    }

    if (!children.isEmpty()) {
      children.add(Separator.getInstance());
    }
    children.addAll(renderers);

    return children.toArray(AnAction.EMPTY_ARRAY);
  }

  @Override
  public void update(@NotNull final AnActionEvent event) {
    if(!DebuggerAction.isFirstStart(event)) {
      return;
    }

    myChildren = AnAction.EMPTY_ARRAY;
    final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(event.getDataContext());
    final List<JavaValue> values = getSelectedValues(event);
    if (values.isEmpty()) {
      event.getPresentation().setEnabledAndVisible(false);
      return;
    }

    final DebugProcessImpl process = debuggerContext.getDebugProcess();
    if (process == null) {
      event.getPresentation().setEnabled(false);
      return;
    }

    process.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        myChildren = calcChildren(values, process.getProject());
        DebuggerAction.enableAction(event, myChildren.length > 0);
      }
    });
  }

  @NotNull
  public static List<JavaValue> getSelectedValues(AnActionEvent event) {
    return StreamEx.of(XDebuggerTreeActionBase.getSelectedNodes(event.getDataContext()))
      .map(XValueNodeImpl::getValueContainer)
      .select(JavaValue.class)
      .toList();
  }
}
