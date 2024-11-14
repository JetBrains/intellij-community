// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.UserRenderersConfigurable;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.sun.jdi.Type;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CreateRendererAction extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    final List<JavaValue> values = ViewAsGroup.getSelectedValues(e);
    if (values.size() != 1) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent event) {
    final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(event.getDataContext());
    final List<JavaValue> values = ViewAsGroup.getSelectedValues(event);
    if (values.size() != 1) {
      return;
    }

    final JavaValue javaValue = values.get(0);

    final DebugProcessImpl process = debuggerContext.getDebugProcess();
    if (process == null) {
      return;
    }

    final Project project = event.getProject();

    javaValue.getEvaluationContext().getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        Type type = javaValue.getDescriptor().getType();
        final String name = type != null ? type.name() : null;
        DebuggerUIUtil.invokeLater(() -> {
          final UserRenderersConfigurable ui = new UserRenderersConfigurable();
          ConfigurableBase<UserRenderersConfigurable, NodeRendererSettings> configurable =
            new ConfigurableBase<>(
              "reference.idesettings.debugger.typerenderers",
              JavaDebuggerBundle.message("user.renderers.configurable.display.name"),
              "reference.idesettings.debugger.typerenderers") {
              @NotNull
              @Override
              protected NodeRendererSettings getSettings() {
                return NodeRendererSettings.getInstance();
              }

              @Override
              protected UserRenderersConfigurable createUi() {
                return ui;
              }
            };
          SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable);
          if (name != null) {
            NodeRenderer renderer = NodeRendererSettings.getInstance().createCompoundReferenceRenderer(
              StringUtil.getShortName(name),
              name,
              null,
              null);
            renderer.setEnabled(true);
            ui.addRenderer(renderer);
          }
          editor.show();
        });
      }
    });
  }
}
