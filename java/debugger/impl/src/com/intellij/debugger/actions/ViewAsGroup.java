// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ViewAsGroup extends ActionGroup implements DumbAware, UpdateInBackground {
  private static final Logger LOG = Logger.getInstance(ViewAsGroup.class);

  public ViewAsGroup() {
    super(Presentation.NULL_STRING, true);
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
  public AnAction @NotNull [] getChildren(@Nullable final AnActionEvent e) {
    if (e == null) {
      return EMPTY_ARRAY;
    }
    DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    DebugProcessImpl process = debuggerContext.getDebugProcess();
    if (process == null) {
      return EMPTY_ARRAY;
    }

    List<JavaValue> values = getSelectedValues(e);
    if (!values.isEmpty()) {
      CompletableFuture<List<NodeRenderer>> future = new CompletableFuture<>();
      boolean scheduled = process.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
        @Override
        public void threadAction(@NotNull SuspendContextImpl suspendContext) {
          getApplicableRenderers(values, process)
            .whenComplete((renderers, throwable) -> DebuggerUtilsAsync.completeFuture(renderers, throwable, future));
        }

        @Override
        protected void commandCancelled() {
          future.cancel(false);
        }
      });
      if (scheduled) {
        List<NodeRenderer> rs = ProgressIndicatorUtils.awaitWithCheckCanceled(future);
        if (ContainerUtil.isEmpty(rs)) {
          return EMPTY_ARRAY;
        }
        List<AnAction> children = new ArrayList<>();
        AnAction[] viewAsActions =
          ((DefaultActionGroup)ActionManager.getInstance().getAction("Debugger.Representation")).getChildren(null);
        for (AnAction viewAsAction : viewAsActions) {
          if (viewAsAction instanceof AutoRendererAction) {
            if (rs.size() > 1) {
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
        children.addAll(ContainerUtil.map(rs, RendererAction::new));

        return children.toArray(EMPTY_ARRAY);
      }
    }
    return EMPTY_ARRAY;
  }

  @NotNull
  private static CompletableFuture<List<NodeRenderer>> getApplicableRenderers(List<JavaValue> values, DebugProcessImpl process) {
    List<CompletableFuture<List<NodeRenderer>>> futures = new ArrayList<>(values.size());
    for (JavaValue value : values) {
      if (value instanceof JavaReferringObjectsValue) { // disable for any referrers at all
        return CompletableFuture.completedFuture(Collections.emptyList());
      }
      ValueDescriptorImpl valueDescriptor = value.getDescriptor();
      if (!valueDescriptor.isValueValid()) {
        return CompletableFuture.completedFuture(Collections.emptyList());
      }
      futures.add(process.getApplicableRenderers(valueDescriptor.getType()));
    }

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(__ -> {
      List<NodeRenderer> res = null;
      for (CompletableFuture<List<NodeRenderer>> future : futures) {
        List<NodeRenderer> list = future.join();
        if (res == null) {
          res = list;
        }
        else {
          res.retainAll(list);
        }
      }
      return ContainerUtil.notNullize(res);
    });
  }

  @Override
  public void update(@NotNull final AnActionEvent event) {
    DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(event.getDataContext());
    if (getSelectedValues(event).isEmpty() || debuggerContext.getDebugProcess() == null) {
      event.getPresentation().setEnabledAndVisible(false);
      return;
    }
    event.getPresentation().setEnabledAndVisible(true);
  }

  @NotNull
  public static List<JavaValue> getSelectedValues(@NotNull AnActionEvent event) {
    List<XValueNodeImpl> selectedNodes = event.getData(XDebuggerTree.SELECTED_NODES);
    if (selectedNodes == null) {
      return Collections.emptyList();
    }
    return StreamEx.of(selectedNodes)
      .map(XValueNodeImpl::getValueContainer)
      .select(JavaValue.class)
      .toList();
  }
}
