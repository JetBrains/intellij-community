// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.UserRenderersConfigurable;
import com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer;
import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.sun.jdi.Type;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CreateRendererActionUtils {
  public static void showCreateRendererDialog(final JavaValue javaValue) {
    EvaluationContextImpl evaluationContext = javaValue.getEvaluationContext();
    Project project = evaluationContext.getProject();
    evaluationContext.getManagerThread().schedule(new SuspendContextCommandImpl(evaluationContext.getSuspendContext()) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        Type type = javaValue.getDescriptor().getType();
        final String name = type != null ? type.name() : null;
        DebuggerUIUtil.invokeLater(() -> {
          final UserRenderersConfigurable ui = new UserRenderersConfigurable();
          ConfigurableBase<UserRenderersConfigurable, NodeRendererSettings> configurable =
            new ConfigurableBase<>(
              "reference.idesettings.debugger.typerenderers",
              JavaDebuggerBundle.message("user.renderers.configurable.display.name"),
              "reference.idesettings.debugger.typerenderers") {
              @Override
              protected @NotNull NodeRendererSettings getSettings() {
                return NodeRendererSettings.getInstance();
              }

              @Override
              protected UserRenderersConfigurable createUi() {
                return ui;
              }
            };
          SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable);
          if (name != null) {
            CompoundReferenceRenderer renderer = NodeRendererSettings.getInstance().createCompoundReferenceRenderer(
              StringUtil.getShortName(name),
              name,
              null,
              null);
            renderer.setEnabled(true);
            renderer.setHasOverhead(true);
            ui.addRenderer(renderer);
          }
          editor.show();
        });
      }
    });
  }
}
