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
package com.intellij.execution.compound;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider;
import com.intellij.execution.impl.RunConfigurationSelector;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.ide.DataManager;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.MultiSelectionListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CompoundRunConfigurationSettingsEditor extends SettingsEditor<CompoundRunConfiguration> {
  private final JBList myList;
  private final RunManagerImpl myRunManager;
  private final SortedListModel<RunConfiguration> myModel;
  private CompoundRunConfiguration mySnapshot;

  public CompoundRunConfigurationSettingsEditor(@NotNull Project project) {
    myRunManager = RunManagerImpl.getInstanceImpl(project);
    myModel = new SortedListModel<>(CompoundRunConfiguration.COMPARATOR);
    myList = new JBList(myModel);
    myList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        RunConfiguration configuration = myModel.get(index);
        setIcon(configuration.getType().getIcon());
        append(configuration.getType().getDisplayName() + " '" + configuration.getName() + "'");
      }
    });
    myList.setVisibleRowCount(15);
  }


  private boolean canBeAdded(@NotNull RunConfiguration candidate, @NotNull final CompoundRunConfiguration root) {
    if (candidate.getType() == root.getType() && candidate.getName().equals(root.getName())) return false;
    List<BeforeRunTask<?>> tasks = myRunManager.getBeforeRunTasks(candidate);
    for (BeforeRunTask task : tasks) {
      if (task instanceof RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask) {
        RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask runTask
          = (RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask)task;
        RunnerAndConfigurationSettings settings = runTask.getSettings();
        if (settings != null) {
         if (!canBeAdded(settings.getConfiguration(), root)) return false;
        }
      }
    }
    if (candidate instanceof CompoundRunConfiguration) {
      for (RunConfiguration configuration : ((CompoundRunConfiguration)candidate).getConfigurations()) {
        if (!canBeAdded(configuration, root)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  protected void resetEditorFrom(@NotNull CompoundRunConfiguration compoundRunConfiguration) {
    myModel.clear();
    myModel.addAll(compoundRunConfiguration.getConfigurations());
    mySnapshot = compoundRunConfiguration;
  }

  @Override
  protected void applyEditorTo(@NotNull CompoundRunConfiguration compoundConfiguration) throws ConfigurationException {
    Set<RunConfiguration> checked = new THashSet<>();
    for (int i = 0; i < myModel.getSize(); i++) {
      RunConfiguration configuration = myModel.get(i);
        String message =
          LangBundle.message("compound.run.configuration.cycle", configuration.getType().getDisplayName(), configuration.getName());
        if (!canBeAdded(configuration, compoundConfiguration)) {
          throw new ConfigurationException(message);
        }

        checked.add(configuration);
    }
    compoundConfiguration.setConfigurations(checked);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList);
    return decorator.disableUpDownActions().setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final List<RunConfiguration> all = new ArrayList<>();
        for (ConfigurationType type : myRunManager.getConfigurationFactoriesWithoutUnknown()) {
          for (RunnerAndConfigurationSettings settings : myRunManager.getConfigurationSettingsList(type)) {
            all.add(settings.getConfiguration());
          }
        }

        final List<RunConfiguration> configurations = ContainerUtil.filter(all,
                                                                           configuration -> !mySnapshot.getConfigurations().contains(configuration) && canBeAdded(configuration, mySnapshot));
        JBPopupFactory.getInstance().createListPopup(new MultiSelectionListPopupStep<RunConfiguration>(null, configurations){
          @Nullable
          @Override
          public ListSeparator getSeparatorAbove(RunConfiguration value) {
            int i = configurations.indexOf(value);
            if (i <1) return null;
            RunConfiguration previous = configurations.get(i - 1);
            return value.getType() != previous.getType() ? new ListSeparator() : null;
          }

          @Override
          public Icon getIconFor(RunConfiguration value) {
            return value.getType().getIcon();
          }

          @Override
          public boolean isSpeedSearchEnabled() {
            return true;
          }

          @NotNull
          @Override
          public String getTextFor(RunConfiguration value) {
            return value.getName();
          }

          @Override
          public PopupStep<?> onChosen(List<RunConfiguration> selectedValues, boolean finalChoice) {
            myList.clearSelection();
            myModel.addAll(selectedValues);
            return FINAL_CHOICE;
          }

        }).showUnderneathOf(decorator.getActionsPanel());
      }
    }).setEditAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        int index = myList.getSelectedIndex();
        if (index == -1) return;
        RunConfiguration configuration = myModel.get(index);
        RunConfigurationSelector selector =
          RunConfigurationSelector.KEY.getData(DataManager.getInstance().getDataContext(button.getContextComponent()));
        if (selector != null) {
          selector.select(configuration);
        }
      }
    }).setToolbarPosition(ActionToolbarPosition.TOP).createPanel();
  }
}
