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
package com.intellij.execution.lineMarker;

import com.intellij.execution.TestStateStorage;
import com.intellij.execution.testframework.TestIconMapper;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public abstract class RunLineMarkerContributor {
  public static final Function<PsiElement, String> RUN_TEST_TOOLTIP_PROVIDER = it -> "Run Test";

  static final LanguageExtension<RunLineMarkerContributor> EXTENSION = new LanguageExtension<>("com.intellij.runLineMarkerContributor");

  public static class Info {
    public final Icon icon;
    public final AnAction[] actions;

    public final Function<PsiElement, String> tooltipProvider;

    public Info(Icon icon, @NotNull AnAction[] actions, @Nullable Function<PsiElement, String> tooltipProvider) {
      this.icon = icon;
      this.actions = actions;
      this.tooltipProvider = tooltipProvider;
    }

    public Info(Icon icon, @Nullable com.intellij.util.Function<PsiElement, String> tooltipProvider, @NotNull AnAction... actions) {
      this.icon = icon;
      this.actions = actions;
      this.tooltipProvider = tooltipProvider == null ? null : it -> tooltipProvider.fun(it);
    }

    public Info(@NotNull final AnAction action) {
      this(action.getTemplatePresentation().getIcon(), new AnAction[]{action}, element -> getText(action, element));
    }

    /**
     * Checks if this Info should replace another one, that is if the other should be discarded.
     */
    public boolean shouldReplace(@NotNull Info other) {
      return false;
    }
  }

  @Nullable
  public abstract Info getInfo(@NotNull PsiElement element);

  @Nullable("null means disabled")
  protected static String getText(@NotNull AnAction action, @NotNull PsiElement element) {
    DataContext parent = DataManager.getInstance().getDataContext();
    DataContext dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.PSI_ELEMENT.getName(), element, parent);
    return action instanceof ExecutorAction ? ((ExecutorAction)action).getActionName(dataContext) : null;
  }

  protected static Icon getTestStateIcon(String url, Project project, boolean isClass) {
    TestStateStorage.Record state = TestStateStorage.getInstance(project).getState(url);
    return getTestStateIcon(state, isClass);
  }

  protected static Icon getTestStateIcon(@Nullable TestStateStorage.Record state, boolean isClass) {
    if (state != null) {
      TestStateInfo.Magnitude magnitude = TestIconMapper.getMagnitude(state.magnitude);
      if (magnitude != null) {
        switch (magnitude) {
          case ERROR_INDEX:
          case FAILED_INDEX:
            return AllIcons.RunConfigurations.TestState.Red2;
          case PASSED_INDEX:
          case COMPLETE_INDEX:
            return AllIcons.RunConfigurations.TestState.Green2;
          default:
        }
      }
    }
    return isClass ? AllIcons.RunConfigurations.TestState.Run_run : AllIcons.RunConfigurations.TestState.Run;
  }
}
