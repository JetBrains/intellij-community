// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.testframework.TestIconMapper;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.mapNotNull;

/**
 * Allows adding an editor gutter icon associated with a PSI element that can be run
 * (e.g., a test, a main method, an application, etc.).
 */
public abstract class RunLineMarkerContributor implements PossiblyDumbAware {
  public static final Function<PsiElement, String> RUN_TEST_TOOLTIP_PROVIDER = it -> ExecutionBundle.message("run.text");

  @ApiStatus.Internal
  public static final LanguageExtension<RunLineMarkerContributor> EXTENSION = new LanguageExtension<>("com.intellij.runLineMarkerContributor");

  /**
   * Creates test run line marker info with a given icon and available executor actions.
   */
  public static @NotNull Info withExecutorActions(@NotNull Icon icon) {
    return new Info(icon, ExecutorAction.getActions(1), RUN_TEST_TOOLTIP_PROVIDER);
  }

  public static class Info {
    public final Icon icon;
    public final AnAction[] actions;

    public final Function<? super PsiElement, String> tooltipProvider;

    public Info(Icon icon, AnAction @NotNull [] actions) {
      this(icon, actions, defaultTooltipProvider(actions));
    }

    public Info(Icon icon, AnAction @NotNull [] actions, @Nullable Function<? super PsiElement, String> tooltipProvider) {
      this.icon = icon;
      this.actions = actions;
      this.tooltipProvider = tooltipProvider;
    }

    /** @deprecated Use other constructors instead */
    @Deprecated(forRemoval = true)
    public Info(Icon icon, @Nullable com.intellij.util.Function<? super PsiElement, String> tooltipProvider, AnAction @NotNull ... actions) {
      this(icon, actions, tooltipProvider == null ? null : it -> tooltipProvider.fun(it));
    }

    public Info(@NotNull AnAction action) {
      this(action.getTemplatePresentation().getIcon(), ContainerUtil.ar(action));
    }

    /**
     * Checks if this Info should replace another one, that is if the other should be discarded.
     */
    public boolean shouldReplace(@NotNull Info other) {
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(icon) + Arrays.hashCode(actions) + Objects.hashCode(tooltipProvider);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Info info &&
             Objects.equals(icon, info.icon)
             && Arrays.equals(actions, info.actions)
             && Objects.equals(tooltipProvider, info.tooltipProvider);
    }
  }

  /**
   * Returns information about gutter icon, its tooltip, and available run actions for a given PSI element.
   */
  public abstract @Nullable Info getInfo(@NotNull PsiElement element);

  /**
   * Returns information about gutter icon, its tooltip, and available run actions for a given PSI element.
   * Implement if creating the information is slow.
   * @see com.intellij.codeInsight.daemon.LineMarkerProvider#collectSlowLineMarkers
   */
  public Info getSlowInfo(@NotNull PsiElement element) {
    return null;
  }

  /**
   * @param file any file with a language this contributor is registered for
   * @return whether there's no possibility that a {@link com.intellij.execution.actions.RunConfigurationProducer} would
   * return a configuration not returned by this contributor in this file. Used to speed up "Run..." context action update.
   */
  public boolean producesAllPossibleConfigurations(@NotNull PsiFile file) {
    return true;
  }

  /** @deprecated Use {@link Info#Info(Icon, AnAction...)} and avoid manual {@link #getText} completely */
  @Deprecated(forRemoval = true)
  protected static @Nullable String getText(@NotNull AnAction action, @NotNull PsiElement element) {
    return defaultTooltipProvider(ContainerUtil.ar(action)).apply(element);
  }

  /** @deprecated Use {@link Info#Info(Icon, AnAction[])} and avoid manual {@link #getText} completely */
  @Deprecated(forRemoval = true)
  protected static @Nullable String getText(@NotNull AnAction action, @NotNull AnActionEvent event) {
    if (!(action instanceof ExecutorAction)) return null;
    event.getPresentation().copyFrom(action.getTemplatePresentation());
    event.getPresentation().setEnabledAndVisible(true);
    action.update(event);
    if (!event.getPresentation().isEnabledAndVisible()) {
      return null;
    }
    return event.getPresentation().getText();
  }

  /** @deprecated  Use {@link #defaultTooltipProvider} instead */
  @Deprecated(forRemoval = true)
  protected static @NotNull AnActionEvent createActionEvent(@NotNull PsiElement element) {
    DataContext dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, element.getProject())
      .add(CommonDataKeys.PSI_ELEMENT, element)
      .build();
    AnActionEvent event = AnActionEvent.createFromDataContext("RunLineMarkerContributor.Tooltip", null, dataContext);
    Utils.initUpdateSession(event);
    return event;
  }

  protected static @NotNull Icon getTestStateIcon(String url, @NotNull Project project, boolean isClass) {
    return getTestStateIcon(TestStateStorage.getInstance(project).getState(url), isClass);
  }

  protected static @NotNull Icon getTestStateIcon(@Nullable TestStateStorage.Record state, boolean isClass) {
    if (state != null) {
      TestStateInfo.Magnitude magnitude = TestIconMapper.getMagnitude(state.magnitude);
      if (magnitude != null) {
        switch (magnitude) {
          case ERROR_INDEX, FAILED_INDEX -> {
            return AllIcons.RunConfigurations.TestState.Red2;
          }
          case PASSED_INDEX, COMPLETE_INDEX -> {
            return AllIcons.RunConfigurations.TestState.Green2;
          }
          default -> {
          }
        }
      }
    }
    return isClass ? AllIcons.RunConfigurations.TestState.Run_run : AllIcons.RunConfigurations.TestState.Run;
  }

  private static @NotNull Function<? super PsiElement, String> defaultTooltipProvider(@NotNull AnAction @NotNull [] actions) {
    return new TooltipProvider(actions);
  }

  private record TooltipProvider(@NotNull AnAction @NotNull [] actions) implements Function<PsiElement, String> {
    @Override
    public String apply(@NotNull PsiElement element) {
      // we could have run parallel action group expand here if it was EDT
      AnActionEvent event = createActionEvent(element);
      return join(mapNotNull(actions, action -> getText(action, event)), "\n");
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(actions);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof TooltipProvider other && Arrays.equals(actions, other.actions);
    }
  }
}
