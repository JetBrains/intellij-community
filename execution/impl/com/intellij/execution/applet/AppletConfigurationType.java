package com.intellij.execution.applet;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;

import javax.swing.*;

public class AppletConfigurationType implements LocatableConfigurationType {
  private final ConfigurationFactory myFactory;
  private static final Icon ICON = IconLoader.getIcon("/runConfigurations/applet.png");

  /**reflection*/
  AppletConfigurationType() {
    myFactory = new ConfigurationFactory(this) {
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new AppletConfiguration("", project, this);
      }
    };
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getDisplayName() {
    return ExecutionBundle.message("applet.configuration.name");
  }

  public String getConfigurationTypeDescription() {
    return ExecutionBundle.message("applet.configuration.description");
  }

  public Icon getIcon() {
    return ICON;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myFactory};
  }

  public RunnerAndConfigurationSettings createConfigurationByLocation(Location location) {
    location = ExecutionUtil.stepIntoSingleClass(location);
    final Project project = location.getProject();
    final PsiElement element = location.getPsiElement();
    final PsiClass aClass = getAppletClass(element, PsiManager.getInstance(project));
    if (aClass == null) return null;
    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).createConfiguration("", getConfigurationFactories()[0]);
    final AppletConfiguration configuration = (AppletConfiguration)settings.getConfiguration();
    configuration.MAIN_CLASS_NAME = ExecutionUtil.getRuntimeQualifiedName(aClass);
    configuration.setModule(new JUnitUtil.ModuleOfClass().convert(aClass));
    configuration.setName(configuration.getGeneratedName());
    return settings;
  }

  public boolean isConfigurationByElement(final RunConfiguration configuration, final Project project, final PsiElement element) {
    final PsiClass aClass = getAppletClass(element, PsiManager.getInstance(project));
    return aClass != null &&
           Comparing.equal(ExecutionUtil.getRuntimeQualifiedName(aClass), ((AppletConfiguration)configuration).MAIN_CLASS_NAME);
  }

  private static PsiClass getAppletClass(PsiElement element, final PsiManager manager) {
    while (element != null) {
      if (element instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)element;
        if (isAppletClass(aClass, manager)){
          return aClass;
        }
      }
      element = element.getParent();
    }
    return null;
  }

  private static boolean isAppletClass(final PsiClass aClass, final PsiManager manager) {
    if (!ExecutionUtil.isRunnableClass(aClass)) return false;

    final Module module = ExecutionUtil.findModule(aClass);
    final GlobalSearchScope scope = module != null
                              ? GlobalSearchScope.moduleWithLibrariesScope(module)
                              : GlobalSearchScope.projectScope(manager.getProject());
    PsiClass appletClass = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.applet.Applet", scope);
    if (appletClass != null) {
      if (aClass.isInheritor(appletClass, true)) return true;
    }
    appletClass = JavaPsiFacade.getInstance(manager.getProject()).findClass("javax.swing.JApplet", scope);
    if (appletClass != null) {
      if (aClass.isInheritor(appletClass, true)) return true;
    }
    return false;
  }


  public String getComponentName() {
    return "Applet";
  }

  public static AppletConfigurationType getInstance() {
    return ApplicationManager.getApplication().getComponent(AppletConfigurationType.class);
  }
}