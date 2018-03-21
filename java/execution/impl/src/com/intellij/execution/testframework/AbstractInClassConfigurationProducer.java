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

package com.intellij.execution.testframework;

import com.intellij.execution.JavaTestConfigurationBase;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit.InheritorChooser;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AbstractInClassConfigurationProducer<T extends JavaTestConfigurationBase> extends AbstractJavaTestConfigurationProducer<T> {
  private static final Logger LOG = Logger.getInstance(AbstractInClassConfigurationProducer.class);

  protected AbstractInClassConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }


  public void onFirstRun(@NotNull final ConfigurationFromContext configuration,
                         @NotNull final ConfigurationContext fromContext,
                         @NotNull Runnable performRunnable) {
    final PsiElement psiElement = configuration.getSourceElement();
    if (psiElement instanceof PsiMethod || psiElement instanceof PsiClass) {

      final PsiMethod psiMethod;
      final PsiClass containingClass;

      if (psiElement instanceof PsiMethod) {
        psiMethod = (PsiMethod)psiElement;
        containingClass = psiMethod.getContainingClass();
      }
      else {
        psiMethod = null;
        containingClass = (PsiClass)psiElement;
      }

      final InheritorChooser inheritorChooser = new InheritorChooser() {
        @Override
        protected void runForClasses(List<PsiClass> classes, PsiMethod method, ConfigurationContext context, Runnable performRunnable) {
          ((T)configuration.getConfiguration()).bePatternConfiguration(classes, method);
          super.runForClasses(classes, method, context, performRunnable);
        }

        @Override
        protected void runForClass(PsiClass aClass,
                                   PsiMethod psiMethod,
                                   ConfigurationContext context,
                                   Runnable performRunnable) {
          if (psiElement instanceof PsiMethod) {
            final Project project = psiMethod.getProject();
            final MethodLocation methodLocation = new MethodLocation(project, psiMethod, PsiLocation.fromPsiElement(aClass));
            ((T)configuration.getConfiguration()).beMethodConfiguration(methodLocation);
          }
          else {
            ((T)configuration.getConfiguration()).beClassConfiguration(aClass);
          }
          super.runForClass(aClass, psiMethod, context, performRunnable);
        }
      };
      if (inheritorChooser.runMethodInAbstractClass(fromContext, performRunnable, psiMethod, containingClass,
                                                    aClass -> aClass.hasModifierProperty(PsiModifier.ABSTRACT) && isTestClass(aClass))) {
        return;
      }
    }
    super.onFirstRun(configuration, fromContext, performRunnable);
  }

  protected boolean setupConfigurationFromContext(T configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    if (isMultipleElementsSelected(context)) {
      return false;
    }

    final Location contextLocation = context.getLocation();
    setupConfigurationParamName(configuration, contextLocation);

    PsiClass psiClass = null;
    PsiElement element = context.getPsiLocation();
    while (element != null) {
      if (element instanceof PsiClass && isTestClass((PsiClass)element)) {
        psiClass = (PsiClass)element;
        break;
      }
      else if (element instanceof PsiMember) {
        psiClass = contextLocation instanceof MethodLocation ? ((MethodLocation)contextLocation).getContainingClass()
                                                             : contextLocation instanceof PsiMemberParameterizedLocation 
                                                               ? ((PsiMemberParameterizedLocation)contextLocation).getContainingClass()
                                                               : ((PsiMember)element).getContainingClass();
        if (isTestClass(psiClass)) {
          break;
        }
      }
      else if (element instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner)element).getClasses();
        if (classes.length == 1) {
          psiClass = classes[0];
          break;
        }
      }
      element = element.getParent();
    }
    if (!isTestClass(psiClass)) return false;

    PsiElement psiElement = psiClass;
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(context);
    setupConfigurationModule(context, configuration);
    final Module originalModule = configuration.getConfigurationModule().getModule();
    configuration.beClassConfiguration(psiClass);

    PsiMethod method = PsiTreeUtil.getParentOfType(context.getPsiLocation(), PsiMethod.class, false);
    while (method != null) {
      if (isTestMethod(false, method)) {
        configuration.beMethodConfiguration(MethodLocation.elementInClass(method, psiClass));
        psiElement = method;
      }
      method = PsiTreeUtil.getParentOfType(method, PsiMethod.class);
    }

    configuration.restoreOriginalModule(originalModule);
    Module module = configuration.getConfigurationModule().getModule();
    if (module == null) {
      PsiFile containingFile = psiClass.getContainingFile();
      LOG.error("No module found", new Attachment("context.txt",
                                                  "generated name:" + configuration.getName() +
                                                  "; valid: " + psiClass.isValid() +
                                                  "; physical: " + psiClass.isPhysical() +
                                                  "; className: " + psiClass.getQualifiedName() +
                                                  "; file: " + containingFile +
                                                  "; module: " + ModuleUtilCore.findModuleForPsiElement(psiClass) + 
                                                  "; original module: " + originalModule));
      return false;
    }
    settings.setName(configuration.getName());
    sourceElement.set(psiElement);
    return true;
  }
}