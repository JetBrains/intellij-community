// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ViewAsGroup extends ActionGroup implements DumbAware {
  private static final Logger LOG = Logger.getInstance(ViewAsGroup.class);

  private volatile AnAction[] myChildren = AnAction.EMPTY_ARRAY;

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
    return myChildren;
  }

  private void updateChildren(List<JavaValue> values, Project project, AnActionEvent event) {
    getApplicableRenderers(values, project).thenAccept(rs -> {
      List<RendererAction> renderers = StreamEx.of(rs).map(RendererAction::new).toList();
      if (ContainerUtil.isEmpty(renderers)) {
        return;
      }

      List<AnAction> children = new ArrayList<>();
      AnAction[] viewAsActions =
        ((DefaultActionGroup)ActionManager.getInstance().getAction(DebuggerActions.REPRESENTATION_LIST)).getChildren(null);
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

      myChildren = children.toArray(AnAction.EMPTY_ARRAY);
      DebuggerAction.enableAction(event, myChildren.length > 0);
    });
  }

  @NotNull
  private static CompletableFuture<List<NodeRenderer>> getApplicableRenderers(List<JavaValue> values, Project project) {
    List<CompletableFuture<List<NodeRenderer>>> futures = new ArrayList<>(values.size());
    for (JavaValue value : values) {
      if (value instanceof JavaReferringObjectsValue) { // disable for any referrers at all
        return CompletableFuture.completedFuture(Collections.emptyList());
      }
      ValueDescriptorImpl valueDescriptor = value.getDescriptor();
      if (!valueDescriptor.isValueValid()) {
        return CompletableFuture.completedFuture(Collections.emptyList());
      }
      futures.add(DebuggerUtilsImpl.getApplicableRenderers(NodeRendererSettings.getInstance().getAllRenderers(project),
                                                            valueDescriptor.getType()));
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
        updateChildren(values, process.getProject(), event);
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
