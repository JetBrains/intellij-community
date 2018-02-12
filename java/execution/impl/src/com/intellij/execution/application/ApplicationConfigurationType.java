/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.application;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ApplicationConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory;

  public ApplicationConfigurationType() {
    myFactory = new ConfigurationFactoryEx(this) {
      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new ApplicationConfiguration("", project, ApplicationConfigurationType.this);
      }

      @Override
      public void onNewConfigurationCreated(@NotNull RunConfiguration configuration) {
        ((ModuleBasedConfiguration)configuration).onNewConfigurationCreated();
      }
    };
  }

  @Override
  public String getDisplayName() {
    return ExecutionBundle.message("application.configuration.name");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return ExecutionBundle.message("application.configuration.description");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.RunConfigurations.Application;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @Nullable
  public static PsiClass getMainClass(PsiElement element) {
    while (element != null) {
      if (element instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)element;
        if (PsiMethodUtil.findMainInClass(aClass) != null) {
          return aClass;
        }
      }
      else if (element instanceof PsiJavaFile) {
        final PsiClass[] classes = ((PsiJavaFile)element).getClasses();
        for (PsiClass aClass : classes) {
          if (PsiMethodUtil.findMainInClass(aClass) != null) {
            return aClass;
          }
        }
      }
      element = element.getParent();
    }
    return null;
  }


  @Override
  @NotNull
  public String getId() {
    return "Application";
  }

  @NotNull
  public static ApplicationConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(ApplicationConfigurationType.class);
  }
}