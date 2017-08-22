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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.settings.UserRenderersConfigurable;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
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
  public void update(AnActionEvent e) {
    final List<JavaValue> values = ViewAsGroup.getSelectedValues(e);
    if (values.size() != 1) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

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

    process.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
      public void threadAction() {
        Type type = javaValue.getDescriptor().getType();
        final String name = type != null ? type.name() :null;
        DebuggerUIUtil.invokeLater(() -> {
          final UserRenderersConfigurable ui = new UserRenderersConfigurable();
          ConfigurableBase<UserRenderersConfigurable, NodeRendererSettings> configurable =
            new ConfigurableBase<UserRenderersConfigurable, NodeRendererSettings>(
                                                                    "reference.idesettings.debugger.typerenderers",
                                                                    DebuggerBundle.message("user.renderers.configurable.display.name"),
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
            NodeRenderer renderer = NodeRendererSettings.getInstance().createCompoundTypeRenderer(
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
