// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AbstractInClassConfigurationProducer<T extends JavaTestConfigurationBase> extends AbstractJavaTestConfigurationProducer<T> {
  private static final Logger LOG = Logger.getInstance(AbstractInClassConfigurationProducer.class);

  /**
   * @deprecated Override {@link #getConfigurationFactory()}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected AbstractInClassConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  protected AbstractInClassConfigurationProducer() {
  }

  @Override
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
          ReadAction.nonBlocking(() -> {
              ((T)configuration.getConfiguration()).bePatternConfiguration(classes, method);
              if (!classes.isEmpty()) {
                PsiClass containerClass = psiElement instanceof PsiMethod ? ((PsiMethod)psiElement).getContainingClass() 
                                                                          : ((PsiClass)psiElement);
                setNestedClass(classes.get(0), containerClass);
              }
            })
            .finishOnUiThread(ModalityState.NON_MODAL, v -> super.runForClasses(classes, method, context, performRunnable))
            .submit(AppExecutorUtil.getAppExecutorService());
        }

        @Override
        protected void runForClass(PsiClass aClass,
                                   PsiMethod psiMethod,
                                   ConfigurationContext context,
                                   Runnable performRunnable) {
          PsiClass containerClass;
          if (psiElement instanceof PsiMethod) {
            final Project project = psiMethod.getProject();
            final MethodLocation methodLocation = new MethodLocation(project, psiMethod, PsiLocation.fromPsiElement(aClass));
            ((T)configuration.getConfiguration()).beMethodConfiguration(methodLocation);
            containerClass = psiMethod.getContainingClass();
          }
          else {
            ((T)configuration.getConfiguration()).beClassConfiguration(aClass);
            containerClass = (PsiClass)psiElement;
          }
          setNestedClass(aClass, containerClass);
          super.runForClass(aClass, psiMethod, context, performRunnable);
        }

        private void setNestedClass(PsiClass aClass, PsiClass containerClass) {
          if (containerClass != null && !aClass.isInheritor(containerClass, true)) {
            for (PsiClass innerClass : aClass.getAllInnerClasses()) {
              //when there are multiple inners with the same super - we just take the first for now; 
              //otherwise chooser should have more than one step
              if (InheritanceUtil.isInheritorOrSelf(innerClass, containerClass, true)) {
                ((T)configuration.getConfiguration()).withNestedClass(innerClass);
                break;
              }
            }
          }
        }
      };
      if (inheritorChooser.runMethodInAbstractClass(fromContext, performRunnable, psiMethod, containingClass,
                                                    aClass -> aClass.hasModifierProperty(PsiModifier.ABSTRACT) && isTestClass(aClass))) {
        return;
      }
    }
    super.onFirstRun(configuration, fromContext, performRunnable);
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull T configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
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
    String classQualifiedName = psiClass.getQualifiedName();
    if (classQualifiedName == null) return false;

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
    if (module == null && psiClass.getManager().isInProject(psiClass)) {
      PsiFile containingFile = psiClass.getContainingFile();
      if (LOG.isDebugEnabled()) {
        LOG.info("No module found: " +
                 "generated name:" + configuration.getName() +
                 "; valid: " + psiClass.isValid() +
                 "; physical: " + psiClass.isPhysical() +
                 "; className: " + classQualifiedName +
                 "; file: " + containingFile +
                 "; module: " + ModuleUtilCore.findModuleForPsiElement(psiClass.getContainingFile()) +
                 "; original module: " + originalModule);
      }
      return false;
    }
    settings.setName(configuration.getName());
    sourceElement.set(psiElement);
    return true;
  }
}