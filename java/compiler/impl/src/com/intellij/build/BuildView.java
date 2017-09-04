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
package com.intellij.build;

import com.intellij.build.events.StartBuildEvent;
import com.intellij.execution.actions.StopAction;
import com.intellij.execution.actions.StopProcessAction;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.FakeRerunAction;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.actions.PinActiveTabAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
class BuildView extends CompositeView<BuildConsoleView, ComponentContainer> implements DataProvider {
  private final ComponentContainer myComponentContainer;
  private final StartBuildEvent myEvent;

  public BuildView(Project project,
                   ComponentContainer componentContainer,
                   StartBuildEvent event,
                   String selectionStateKey,
                   boolean isConsoleEnabledByDefault) {
    super(new BuildTreeConsoleView(project), componentContainer, selectionStateKey, !isConsoleEnabledByDefault);
    myComponentContainer = componentContainer;
    myEvent = event;
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    final DefaultActionGroup rerunActionGroup = new DefaultActionGroup();
    AnAction stopAction = null;
    if (myEvent.getProcessHandler() != null) {
      stopAction = new StopProcessAction("Stop", "Stop", myEvent.getProcessHandler());
    }
    final DefaultActionGroup consoleActionGroup = new DefaultActionGroup() {
      @Override
      public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setVisible(!BuildView.this.isPrimaryConsoleEnabled());
      }
    };
    if (myComponentContainer instanceof BuildConsoleView) {
      final AnAction[] consoleActions = ((BuildConsoleView)myComponentContainer).createConsoleActions();
      for (AnAction anAction : consoleActions) {
        if (anAction instanceof StopAction) {
          if (stopAction == null) {
            stopAction = anAction;
          }
        }
        else if (!(anAction instanceof FakeRerunAction ||
                   anAction instanceof PinActiveTabAction ||
                   anAction instanceof CloseAction)) {
          consoleActionGroup.add(anAction);
        }
      }
    }
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (AnAction anAction : myEvent.getRestartActions()) {
      rerunActionGroup.add(anAction);
    }
    if (stopAction != null) {
      rerunActionGroup.add(stopAction);
    }
    actionGroup.add(rerunActionGroup);
    actionGroup.addSeparator();
    AnAction[] actions = super.createConsoleActions();
    actionGroup.addAll(actions);
    if (actions.length > 0) {
      actionGroup.addSeparator();
    }
    return new AnAction[]{actionGroup, consoleActionGroup};
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    Object data = super.getData(dataId);
    if (data != null) return data;
    if (LangDataKeys.RUN_PROFILE.is(dataId)) {
      ExecutionEnvironment environment = myEvent.getExecutionEnvironment();
      return environment == null ? null : environment.getRunProfile();
    }
    if (LangDataKeys.EXECUTION_ENVIRONMENT.is(dataId)) {
      return myEvent.getExecutionEnvironment();
    }
    return null;
  }
}
