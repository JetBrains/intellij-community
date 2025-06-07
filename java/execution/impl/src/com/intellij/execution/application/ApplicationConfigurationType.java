// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

      @Override
      public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
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

  @Override
  public @NotNull String getDisplayName() {
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
    return "concepts.run.configuration";
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  public static @Nullable PsiClass getMainClass(PsiElement element) {
    while (element != null) {
      if (element instanceof PsiClass aClass) {
        if (PsiMethodUtil.hasMainInClass(aClass)) {
          return aClass;
        }
      }
      else if (element instanceof PsiJavaFile javaFile) {
        final PsiClass[] classes = javaFile.getClasses();
        for (PsiClass aClass : classes) {
          if (PsiMethodUtil.hasMainInClass(aClass)) {
            return aClass;
          }
        }
      }
      element = element.getParent();
    }
    return null;
  }


  @Override
  public @NotNull String getId() {
    return "Application";
  }

  @Override
  public @NotNull String getTag() {
    String id = getId();
    return id.equals("Application") ? "java" : id;
  }

  public static @NotNull ApplicationConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(ApplicationConfigurationType.class);
  }
}