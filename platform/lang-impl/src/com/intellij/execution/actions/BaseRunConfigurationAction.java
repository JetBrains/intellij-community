/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.IndexNotReadyException;
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
import java.util.Comparator;
import java.util.List;

public abstract class BaseRunConfigurationAction extends ActionGroup {
  protected static final Logger LOG = Logger.getInstance("#" + BaseRunConfigurationAction.class.getName());

  protected BaseRunConfigurationAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
    setPopup(true);
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
      final List<RuntimeConfigurationProducer> producers = getEnabledProducers(context);
      if (producers.size() > 1) {
        final AnAction[] children = new AnAction[producers.size()];
        int chldIdx = 0;
        for (final RuntimeConfigurationProducer producer : producers) {
          final ConfigurationType configurationType = producer.getConfigurationType();
          final RunConfiguration configuration = producer.getConfiguration().getConfiguration();
          final String actionName = configuration instanceof LocatableConfiguration
                                    ? StringUtil.unquoteString(suggestRunActionName((LocatableConfiguration)configuration))
                                    : configurationType.getDisplayName();
          children[chldIdx++] = new AnAction(actionName, configurationType.getDisplayName(), configurationType.getIcon()) {
            @Override
            public void actionPerformed(AnActionEvent e) {
              perform(producer, context);
            }
          };
        }
        return children;
      }
    }
    return EMPTY_ARRAY;
  }

  @NotNull
  private List<RuntimeConfigurationProducer> getEnabledProducers(ConfigurationContext context) {
    final List<RuntimeConfigurationProducer> preferred = context.findPreferredProducers();
    if (preferred == null) {
      return Collections.emptyList();
    }

    final List<RuntimeConfigurationProducer> producers = new ArrayList<RuntimeConfigurationProducer>();
    for (RuntimeConfigurationProducer producer : preferred) {
      if (isEnabledFor(producer.getConfiguration().getConfiguration())) {
        producers.add(producer);
      }
    }
    return producers;
  }

  protected boolean isEnabledFor(RunConfiguration configuration) {
    return true;
  }

  @Override
  public boolean canBePerformed(DataContext dataContext) {
    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
    final RunnerAndConfigurationSettings existing = context.findExisting();
    if (existing == null) {
      final List<RuntimeConfigurationProducer> producers = getEnabledProducers(context);
      return producers.size() <= 1;
    }
    return true;
  }

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
    final RunnerAndConfigurationSettings existing = context.findExisting();
    if (existing == null) {
      final List<RuntimeConfigurationProducer> producers = getEnabledProducers(context);
      if (producers.isEmpty()) return;
      if (producers.size() > 1) {
        final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
        Collections.sort(producers, new Comparator<RuntimeConfigurationProducer>() {
          public int compare(final RuntimeConfigurationProducer p1, final RuntimeConfigurationProducer p2) {
            return p1.getConfigurationType().getDisplayName().compareTo(p2.getConfigurationType().getDisplayName());
          }
        });
        final ListPopup popup =
          JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<RuntimeConfigurationProducer>(ExecutionBundle.message("configuration.action.chooser.title"), producers) {
            @NotNull
            public String getTextFor(final RuntimeConfigurationProducer producer) {
              return producer.getConfigurationType().getDisplayName();
            }

            public Icon getIconFor(final RuntimeConfigurationProducer producer) {
              return producer.getConfigurationType().getIcon();
            }

            public PopupStep onChosen(final RuntimeConfigurationProducer producer, final boolean finalChoice) {
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

  private void perform(final RuntimeConfigurationProducer producer, final ConfigurationContext context) {
    final RunnerAndConfigurationSettings configuration = context.getConfiguration(producer);
    if (configuration != null) {
      perform(context);
    }
  }

  protected abstract void perform(ConfigurationContext context);

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
      final List<RuntimeConfigurationProducer> producers = getEnabledProducers(context);
      if (existing == null && !producers.isEmpty()) {
        //todo[nik,anna] it's dirty fix. Otherwise wrong configuration will be returned from context.getConfiguration()
        context.getConfiguration(producers.get(0));
      }
      final String name = suggestRunActionName((LocatableConfiguration)configuration.getConfiguration());
      updatePresentation(presentation, existing != null || producers.size() <= 1 ? " " + name : "", context);
    }
  }

  public static String suggestRunActionName(final LocatableConfiguration configuration) {
    if (!configuration.isGeneratedName()) {
      return "\"" + ProgramRunnerUtil.shortenName(configuration.getName(), 0) + "\"";
    } else return "\"" + configuration.suggestedName() + "\"";
  }

  protected abstract void updatePresentation(Presentation presentation, String actionText, ConfigurationContext context);

}
