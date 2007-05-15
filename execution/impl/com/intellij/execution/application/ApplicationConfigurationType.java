package com.intellij.execution.application;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ApplicationConfigurationType implements LocatableConfigurationType {
  private final ConfigurationFactory myFactory;
  private static final Icon ICON = IconLoader.getIcon("/runConfigurations/application.png");

  /**reflection*/
  public ApplicationConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new ApplicationConfiguration("", project, ApplicationConfigurationType.this);
      }

    };
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getDisplayName() {
    return ExecutionBundle.message("application.configuration.name");
  }

  public String getConfigurationTypeDescription() {
    return ExecutionBundle.message("application.configuration.description");
  }

  public Icon getIcon() {
    return ICON;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public RunnerAndConfigurationSettings createConfigurationByLocation(final Location location) {
    return ApplicationConfigurationProducer.PROTOTYPE.createProducer(location, null).getConfiguration();
  }

  public boolean isConfigurationByElement(final RunConfiguration configuration, final Project project, final PsiElement element) {
    final PsiClass aClass = getMainClass(element);
    if (aClass == null) {
      return false;
    }
    return Comparing.equal(ExecutionUtil.getRuntimeQualifiedName(aClass), ((ApplicationConfiguration)configuration).MAIN_CLASS_NAME);
  }

  public static PsiClass getMainClass(PsiElement element) {
    while (element != null) {
      if (element instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)element;
        if (PsiMethodUtil.findMainInClass(aClass) != null){
          return aClass;
        }
      }
      element = element.getParent();
    }
    return null;
  }


  @NotNull @NonNls
  public String getComponentName() {
    return "Application";
  }

  public static ApplicationConfigurationType getInstance() {
    return ApplicationManager.getApplication().getComponent(ApplicationConfigurationType.class);
  }

}
