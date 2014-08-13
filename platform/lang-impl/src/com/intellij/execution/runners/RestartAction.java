/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.runners;

import com.intellij.execution.Executor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@Deprecated
/**
 * to remove in IDEA 15
 */
public class RestartAction extends FakeRerunAction implements DumbAware, AnAction.TransparentUpdate, Disposable {
  private final RunContentDescriptor myDescriptor;
  private final ExecutionEnvironment myEnvironment;

  public RestartAction(@NotNull RunContentDescriptor descriptor, @NotNull ExecutionEnvironment environment) {
    //noinspection deprecation
    this(environment.getExecutor(), null, descriptor, environment);
  }

  @Deprecated
  /**
   * @deprecated environment must provide runner id
   * to remove in IDEA 15
   */
  public RestartAction(@SuppressWarnings("UnusedParameters") @NotNull Executor executor,
                       @Nullable ProgramRunner runner,
                       @NotNull RunContentDescriptor descriptor,
                       @NotNull ExecutionEnvironment environment) {
    Disposer.register(descriptor, this);
    FakeRerunAction.registry.add(this);

    myEnvironment = runner == null ? environment : RunContentBuilder.fix(environment, runner);
    getTemplatePresentation().setEnabled(false);
    myDescriptor = descriptor;
  }

  @Override
  public void dispose() {
    FakeRerunAction.registry.remove(this);
  }

  @Override
  @NotNull
  protected RunContentDescriptor getDescriptor(AnActionEvent event) {
    return myDescriptor;
  }

  @Override
  @NotNull
  protected ExecutionEnvironment getEnvironment(AnActionEvent event) {
    return myEnvironment;
  }

  public void registerShortcut(JComponent component) {
    registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_RERUN)),
                              component);
  }
}
