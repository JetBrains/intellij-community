/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.console;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class UseConsoleInputAction extends ToggleAction implements DumbAware {
  private final String processInputStateKey;
  private boolean useProcessStdIn;

  public UseConsoleInputAction(@NotNull String processInputStateKey) {
    super("Use Console Input", null, AllIcons.Debugger.CommandLine);

    this.processInputStateKey = processInputStateKey;
    useProcessStdIn = PropertiesComponent.getInstance().getBoolean(processInputStateKey);
  }

  @Override
  public boolean isSelected(@Nullable AnActionEvent event) {
    return !useProcessStdIn;
  }

  @Override
  public void setSelected(AnActionEvent event, boolean state) {
    useProcessStdIn = !state;

    LanguageConsoleView consoleView = (LanguageConsoleView)event.getData(LangDataKeys.CONSOLE_VIEW);
    assert consoleView != null;
    DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(consoleView.getProject());
    PsiFile file = consoleView.getFile();
    daemonCodeAnalyzer.setHighlightingEnabled(file, state);
    daemonCodeAnalyzer.restart(file);
    PropertiesComponent.getInstance().setValue(processInputStateKey, useProcessStdIn);

    List<AnAction> actions = ActionUtil.getActions(consoleView.getConsoleEditor().getComponent());
    ConsoleExecuteAction action = ContainerUtil.findInstance(actions, ConsoleExecuteAction.class);
    action.myExecuteActionHandler.myUseProcessStdIn = !state;
  }
}