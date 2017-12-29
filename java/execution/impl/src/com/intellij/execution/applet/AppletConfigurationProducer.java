// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.applet;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AppletConfigurationProducer extends JavaRuntimeConfigurationProducerBase {
  private PsiClass myPsiClass;

  protected AppletConfigurationProducer() {
    super(AppletConfigurationType.getInstance());
  }

  @Override
  public PsiElement getSourceElement() {
    return myPsiClass;
  }

  @Override
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    location = JavaExecutionUtil.stepIntoSingleClass(location);
    if (location == null) return null;
    final Project project = location.getProject();
    final PsiElement element = location.getPsiElement();
    myPsiClass = getAppletClass(element, PsiManager.getInstance(project));
    if (myPsiClass == null) return null;
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final AppletConfiguration configuration = (AppletConfiguration)settings.getConfiguration();
    configuration.setMainClassName(JavaExecutionUtil.getRuntimeQualifiedName(myPsiClass));
    configuration.setModule(myPsiClass.isValid() ? ModuleUtilCore.findModuleForPsiElement(myPsiClass) : null);
    configuration.setGeneratedName();
    return settings;
  }

  public int compareTo(Object o) {
    return PREFERED;
  }


  @Nullable
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
    if (DumbService.isDumb(manager.getProject())) return false;
    if (!PsiClassUtil.isRunnableClass(aClass, true)) return false;

    final Module module = JavaExecutionUtil.findModule(aClass);
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

  @Override
  protected RunnerAndConfigurationSettings findExistingByElement(Location location,
                                                                 @NotNull List<RunnerAndConfigurationSettings> existingConfigurations,
                                                                 ConfigurationContext context) {
    final PsiClass aClass = getAppletClass(location.getPsiElement(), PsiManager.getInstance(location.getProject()));
    if (aClass != null) {
      for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
        if (Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(aClass),
                            ((AppletConfiguration)existingConfiguration.getConfiguration()).getOptions().getMainClassName())) {
          return existingConfiguration;
        }
      }
    }
    return null;
  }
}
