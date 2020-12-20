// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

// cannot be final because of backward compatibility (~8 external usages)
/**
 * DO NOT extend this class directly.
 */
public class ApplicationConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myFactory;

  public ApplicationConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      @Override
      public Class<? extends BaseState> getOptionsClass() {
        return JvmMainMethodRunConfigurationOptions.class;
      }

      @NotNull
      @Override
      public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new ApplicationConfiguration("", project, ApplicationConfigurationType.this);
      }

      @Override
      public @NotNull String getId() {
        return ApplicationConfigurationType.this.getId();
      }

      @Override
      public boolean isEditableInDumbMode() {
        return true;
      }
    };
  }

  @NotNull
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
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.Application";
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
  @Override
  public String getTag() {
    String id = getId();
    return id.equals("Application") ? "java" : id;
  }

  @NotNull
  public static ApplicationConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(ApplicationConfigurationType.class);
  }
}