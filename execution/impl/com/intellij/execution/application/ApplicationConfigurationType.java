package com.intellij.execution.application;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;

import javax.swing.*;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ApplicationConfigurationType implements LocatableConfigurationType {
  private final ConfigurationFactory myFactory;
  private static final Icon ICON = IconLoader.getIcon("/runConfigurations/application.png");
  @NonNls
  protected static final String STRING_CLASS = "java.lang.String";

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
        if (findMainInClass(aClass) != null){
          return aClass;
        }
      }
      element = element.getParent();
    }
    return null;
  }


  private static PsiMethod findMainInClass(final PsiClass aClass) {
    if (!ConfigurationUtil.MAIN_CLASS.value(aClass)) return null;
    return findMainMethod(aClass);
  }

  public static PsiMethod findMainMethod(final PsiClass aClass) {
    final PsiMethod[] mainMethods = aClass.findMethodsByName("main", false);
    return findMainMethod(mainMethods);
  }

  public static PsiMethod findMainMethod(final PsiMethod[] mainMethods) {
    for (int i = 0; i < mainMethods.length; i++) {
      final PsiMethod mainMethod = mainMethods[i];
      if (isMainMethod(mainMethod)) return mainMethod;
    }
    return null;
  }

  public static boolean isMainMethod(final PsiMethod method) {
    if (method == null) return false;
    if (PsiType.VOID != method.getReturnType()) return false;
    if (!method.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return false;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    final PsiType type = parameters[0].getType();
    if (!(type instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType)type).getComponentType();
    return componentType.equalsToText(STRING_CLASS);
  }


  @NotNull @NonNls
  public String getComponentName() {
    return "Application";
  }

  public static ApplicationConfigurationType getInstance() {
    return ApplicationManager.getApplication().getComponent(ApplicationConfigurationType.class);
  }

  public static boolean hasMainMethod(final PsiClass psiClass) {
    return findMainMethod(psiClass.findMethodsByName("main", true)) != null;
  }
}
