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
package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.util.Consumer;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Irina.Chernushina on 10/8/2015.
 */
public class NewRunConfigurationPopup {
  @NotNull
  public static ListPopup createAddPopup(@NotNull final List<ConfigurationType> typesToShow,
                                         @NotNull final String defaultText,
                                         @NotNull final Consumer<ConfigurationFactory> creator,
                                         @Nullable final ConfigurationType selectedConfigurationType,
                                         @NotNull final Runnable finalStep, boolean showTitle) {

    return JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<ConfigurationType>(
      showTitle ? ExecutionBundle.message("add.new.run.configuration.action2.name") : null, typesToShow) {

      @Override
      @NotNull
      public String getTextFor(final ConfigurationType type) {
        return type != null ? type.getDisplayName() :  defaultText;
      }

      @Override
      public boolean isSpeedSearchEnabled() {
        return true;
      }

      @Override
      public boolean canBeHidden(ConfigurationType value) {
        return true;
      }

      @Override
      public Icon getIconFor(final ConfigurationType type) {
        return type != null ? type.getIcon() : EmptyIcon.ICON_16;
      }

      @Override
      public PopupStep onChosen(final ConfigurationType type, final boolean finalChoice) {
        if (hasSubstep(type)) {
          return getSupStep(type);
        }
        if (type == null) {
          return doFinalStep(finalStep);
        }

        final ConfigurationFactory[] factories = type.getConfigurationFactories();
        if (factories.length > 0) {
          creator.consume(factories[0]);
        }
        return FINAL_CHOICE;
      }

      @Override
      public int getDefaultOptionIndex() {
        return selectedConfigurationType != null ? typesToShow.indexOf(selectedConfigurationType) : super.getDefaultOptionIndex();
      }

      private ListPopupStep getSupStep(final ConfigurationType type) {
        final ConfigurationFactory[] factories = type.getConfigurationFactories();
        Arrays.sort(factories, (factory1, factory2) -> factory1.getName().compareToIgnoreCase(factory2.getName()));
        return new BaseListPopupStep<ConfigurationFactory>(
          ExecutionBundle.message("add.new.run.configuration.action.name", type.getDisplayName()), factories) {

          @Override
          @NotNull
          public String getTextFor(final ConfigurationFactory value) {
            return value.getName();
          }

          @Override
          public Icon getIconFor(final ConfigurationFactory factory) {
            return factory.getIcon();
          }

          @Override
          public PopupStep onChosen(final ConfigurationFactory factory, final boolean finalChoice) {
            creator.consume(factory);
            return FINAL_CHOICE;
          }
        };
      }

      @Override
      public boolean hasSubstep(final ConfigurationType type) {
        return type != null && type.getConfigurationFactories().length > 1;
      }
    });
  }
}
