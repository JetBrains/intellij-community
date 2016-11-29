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

package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BaseRunConfigurationAction extends ActionGroup {
  protected static final Logger LOG = Logger.getInstance("#" + BaseRunConfigurationAction.class.getName());

  protected BaseRunConfigurationAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
    setPopup(true);
    setEnabledInModalContext(true);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return e != null ? getChildren(e.getDataContext()) : EMPTY_ARRAY;
  }

  private AnAction[] getChildren(DataContext dataContext) {
    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
    final RunnerAndConfigurationSettings existing = context.findExisting();
    if (existing == null) {
      final List<ConfigurationFromContext> producers = getConfigurationsFromContext(context);
      if (producers.size() > 1) {
        final AnAction[] children = new AnAction[producers.size()];
        int childIdx = 0;
        for (final ConfigurationFromContext fromContext : producers) {
          final ConfigurationType configurationType = fromContext.getConfigurationType();
          final RunConfiguration configuration = fromContext.getConfiguration();
          final String actionName = configuration instanceof LocatableConfiguration
                                    ? StringUtil.unquoteString(suggestRunActionName((LocatableConfiguration)configuration))
                                    : configurationType.getDisplayName();
          final AnAction anAction = new AnAction(actionName, configurationType.getDisplayName(), configuration.getIcon()) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              perform(fromContext, context);
            }
          };
          anAction.getTemplatePresentation().setText(actionName, false);
          children[childIdx++] = anAction;
        }
        return children;
      }
    }
    return EMPTY_ARRAY;
  }

  @NotNull
  private List<ConfigurationFromContext> getConfigurationsFromContext(ConfigurationContext context) {
    final List<ConfigurationFromContext> fromContext = context.getConfigurationsFromContext();
    if (fromContext == null) {
      return Collections.emptyList();
    }

    final List<ConfigurationFromContext> enabledConfigurations = new ArrayList<>();
    for (ConfigurationFromContext configurationFromContext : fromContext) {
      if (isEnabledFor(configurationFromContext.getConfiguration())) {
        enabledConfigurations.add(configurationFromContext);
      }
    }
    return enabledConfigurations;
  }

  protected boolean isEnabledFor(RunConfiguration configuration) {
    return true;
  }

  @Override
  public boolean canBePerformed(DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project != null && DumbService.isDumb(project)) {
      return false;
    }

    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
    final RunnerAndConfigurationSettings existing = context.findExisting();
    if (existing == null) {
      final List<ConfigurationFromContext> fromContext = getConfigurationsFromContext(context);
      return fromContext.size() <= 1;
    }
    return true;
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
    final RunnerAndConfigurationSettings existing = context.findExisting();
    if (existing == null) {
      final List<ConfigurationFromContext> producers = getConfigurationsFromContext(context);
      if (producers.isEmpty()) return;
      if (producers.size() > 1) {
        final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        Collections.sort(producers, ConfigurationFromContext.NAME_COMPARATOR);
        final ListPopup popup =
          JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<ConfigurationFromContext>(ExecutionBundle.message("configuration.action.chooser.title"), producers) {
            @Override
            @NotNull
            public String getTextFor(final ConfigurationFromContext producer) {
              return producer.getConfigurationType().getDisplayName();
            }

            @Override
            public Icon getIconFor(final ConfigurationFromContext producer) {
              return producer.getConfigurationType().getIcon();
            }

            @Override
            public PopupStep onChosen(final ConfigurationFromContext producer, final boolean finalChoice) {
              perform(producer, context);
              return FINAL_CHOICE;
            }
          });
        final InputEvent event = e.getInputEvent();
        if (event instanceof MouseEvent) {
          popup.show(new RelativePoint((MouseEvent)event));
        } else if (editor != null) {
          popup.showInBestPositionFor(editor);
        } else {
          popup.showInBestPositionFor(dataContext);
        }
      } else {
        perform(producers.get(0), context);
      }
      return;
    }

    perform(context);
  }

  private void perform(final ConfigurationFromContext configurationFromContext, final ConfigurationContext context) {
    RunnerAndConfigurationSettings configurationSettings = configurationFromContext.getConfigurationSettings();
    context.setConfiguration(configurationSettings);
    configurationFromContext.onFirstRun(context, () -> perform(context));
  }

  protected abstract void perform(ConfigurationContext context);

  @Override
  public void update(final AnActionEvent event){
    final ConfigurationContext context = ConfigurationContext.getFromContext(event.getDataContext());
    final Presentation presentation = event.getPresentation();
    final RunnerAndConfigurationSettings existing = context.findExisting();
    RunnerAndConfigurationSettings configuration = existing;
    if (configuration == null) {
      configuration = context.getConfiguration();
    }
    if (configuration == null){
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
    else{
      presentation.setEnabled(true);
      presentation.setVisible(true);
      final List<ConfigurationFromContext> fromContext = getConfigurationsFromContext(context);
      if (existing == null && !fromContext.isEmpty()) {
        //todo[nik,anna] it's dirty fix. Otherwise wrong configuration will be returned from context.getConfiguration()
        context.setConfiguration(fromContext.get(0).getConfigurationSettings());
      }
      final String name = suggestRunActionName((LocatableConfiguration)configuration.getConfiguration());
      updatePresentation(presentation, existing != null || fromContext.size() <= 1 ? name : "", context);
    }
  }

  @Override
  public boolean isDumbAware() {
    return false;
  }

  public static String suggestRunActionName(final LocatableConfiguration configuration) {
    if (configuration instanceof LocatableConfigurationBase && configuration.isGeneratedName()) {
      String actionName = ((LocatableConfigurationBase)configuration).getActionName();
      if (actionName != null) {
        return actionName;
      }
    }
    return ProgramRunnerUtil.shortenName(configuration.getName(), 0);
  }

  protected abstract void updatePresentation(Presentation presentation, @NotNull String actionText, ConfigurationContext context);

}
