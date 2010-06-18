/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 13-May-2010
 */
package com.intellij.execution.applet;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;

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
    final Project project = location.getProject();
    final PsiElement element = location.getPsiElement();
    myPsiClass = getAppletClass(element, PsiManager.getInstance(project));
    if (myPsiClass == null) return null;
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final AppletConfiguration configuration = (AppletConfiguration)settings.getConfiguration();
    configuration.MAIN_CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(myPsiClass);
    configuration.setModule(new JUnitUtil.ModuleOfClass().convert(myPsiClass));
    configuration.setName(configuration.getGeneratedName());
    return settings;
  }

  public int compareTo(Object o) {
    return PREFERED;
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
                                                                 @NotNull RunnerAndConfigurationSettings[] existingConfigurations,
                                                                 ConfigurationContext context) {
    final PsiClass aClass = getAppletClass(location.getPsiElement(), PsiManager.getInstance(location.getProject()));
    if (aClass != null) {
      for (RunnerAndConfigurationSettings existingConfiguration : existingConfigurations) {
        if (Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(aClass),
                            ((AppletConfiguration)existingConfiguration.getConfiguration()).MAIN_CLASS_NAME)) {
          return existingConfiguration;
        }
      }
    }
    return null;
  }
}
