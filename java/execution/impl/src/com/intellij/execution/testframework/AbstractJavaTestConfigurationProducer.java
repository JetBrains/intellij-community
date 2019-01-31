// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.*;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;

import java.util.*;

public abstract class AbstractJavaTestConfigurationProducer<T extends JavaTestConfigurationBase> extends JavaRunConfigurationProducerBase<T> {
  /**
   * @deprecated Override {@link #getConfigurationFactory()}.
   */
  @Deprecated
  protected AbstractJavaTestConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  protected AbstractJavaTestConfigurationProducer() {
  }

  @Contract("null->false")
  protected boolean isTestClass(PsiClass psiClass) {
    if (psiClass != null) {
      JavaTestFramework framework = getCurrentFramework(psiClass);
      return framework != null && framework.isTestClass(psiClass);
    }
    return false;
  }

  protected boolean isTestMethod(boolean checkAbstract, PsiMethod method) {
    JavaTestFramework framework = getCurrentFramework(method.getContainingClass());
    return framework != null && framework.isTestMethod(method, checkAbstract);
  }

  protected JavaTestFramework getCurrentFramework(PsiClass psiClass) {
    if (psiClass != null) {
      ConfigurationType configurationType = getConfigurationType();
      Set<TestFramework> frameworks = TestFrameworks.detectApplicableFrameworks(psiClass);
      return frameworks.stream().filter(framework -> framework instanceof JavaTestFramework && ((JavaTestFramework)framework).isMyConfigurationType(configurationType))
        .map(framework -> (JavaTestFramework)framework)
        .findFirst()
        .orElse(null);
    }
    return null;
  }

  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(T configuration, ConfigurationContext context) {
    if (isMultipleElementsSelected(context)) {
      return false;
    }
    final RunConfiguration predefinedConfiguration = context.getOriginalConfiguration(getConfigurationType());
    final Location contextLocation = context.getLocation();
    if (contextLocation == null) {
      return false;
    }
    Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) {
      return false;
    }
    final PsiElement element = location.getPsiElement();
    RunnerAndConfigurationSettings template = context.getRunManager().getConfigurationTemplate(getConfigurationFactory());
    T templateConfiguration = (T)template.getConfiguration();
    final Module predefinedModule = templateConfiguration.getConfigurationModule().getModule();
    final String vmParameters;
    if (predefinedConfiguration != null) {
      vmParameters = predefinedConfiguration instanceof CommonJavaRunConfigurationParameters
                     ? ((CommonJavaRunConfigurationParameters)predefinedConfiguration).getVMParameters()
                     : null;
    }
    else {
      vmParameters = templateConfiguration.getVMParameters();
    }
    if (!Comparing.strEqual(vmParameters, configuration.getVMParameters())) return false;
    if (differentParamSet(configuration, contextLocation)) return false;

    if (!isApplicableTestType(configuration.getTestType(), context)) return false;

    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass != null && getCurrentFramework(psiClass) == null) return false;

    if (configuration.isConfiguredByElement(element)) {
      final Module configurationModule = configuration.getConfigurationModule().getModule();
      final Module locationModule = location.getModule();
      if (Comparing.equal(locationModule, configurationModule)) return true;
      if ((predefinedModule != null || locationModule == null) && Comparing.equal(predefinedModule, configurationModule)) return true;
    }

    return false;
  }

  protected boolean differentParamSet(T configuration, Location contextLocation) {
    String paramSetName = contextLocation instanceof PsiMemberParameterizedLocation
                          ? configuration.prepareParameterizedParameter(((PsiMemberParameterizedLocation)contextLocation).getParamSetName()) : null;
    return !Comparing.strEqual(paramSetName, configuration.getProgramParameters());
  }


  public Module findModule(ModuleBasedConfiguration configuration, Module contextModule, Set<String> patterns) {
    return JavaExecutionUtil.findModule(contextModule, patterns, configuration.getProject(), psiClass -> isTestClass(psiClass));
  }

  public void collectTestMembers(PsiElement[] psiElements,
                                 boolean checkAbstract,
                                 boolean checkIsTest,
                                 PsiElementProcessor.CollectElements<PsiElement> collectingProcessor) {
    for (PsiElement psiElement : psiElements) {
      if (psiElement instanceof PsiDirectory) {
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)psiElement);
        if (aPackage != null && !collectingProcessor.execute(aPackage)) {
          return;
        }
      }
      else {
        psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiMember.class, false);
        if (psiElement instanceof PsiClassOwner) {
          final PsiClass[] classes = ((PsiClassOwner)psiElement).getClasses();
          for (PsiClass aClass : classes) {
            if ((!checkIsTest && isRequiredVisibility(aClass) || checkIsTest && isTestClass(aClass)) &&
                !collectingProcessor.execute(aClass)) {
              return;
            }
          }
        }
        else if (psiElement instanceof PsiClass) {
          if ((!checkIsTest && isRequiredVisibility((PsiClass)psiElement) ||
               checkIsTest && isTestClass((PsiClass)psiElement)) &&
              !collectingProcessor.execute(psiElement)) {
            return;
          }
        }
        else if (psiElement instanceof PsiMethod) {
          if (checkIsTest && isTestMethod(checkAbstract, (PsiMethod)psiElement) && !collectingProcessor.execute(psiElement)) {
            return;
          }
          if (!checkIsTest) {
            final PsiClass containingClass = ((PsiMethod)psiElement).getContainingClass();
            if (containingClass != null &&
                isRequiredVisibility(containingClass) &&
                !collectingProcessor.execute(psiElement)) {
              return;
            }
          }
        }
      }
    }
  }

  protected boolean isRequiredVisibility(PsiMember psiElement) {
    return psiElement.hasModifierProperty(PsiModifier.PUBLIC);
  }

  protected boolean collectContextElements(DataContext dataContext,
                                           boolean checkAbstract,
                                           boolean checkIsTest,
                                           LinkedHashSet<String> classes,
                                           PsiElementProcessor.CollectElements<PsiElement> processor) {
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (elements != null) {
      return collectTestMembers(elements, checkAbstract, checkIsTest, processor, classes);
    }
    else {
      final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      PsiElement element = null;
      if (editor != null) {
        final PsiFile editorFile = CommonDataKeys.PSI_FILE.getData(dataContext);
        final List<Caret> allCarets = editor.getCaretModel().getAllCarets();
        if (editorFile != null) {
          if (allCarets.size() > 1) {
            final Set<PsiMethod> methods = new LinkedHashSet<>();
            for (Caret caret : allCarets) {
              ContainerUtil
                .addIfNotNull(methods, PsiTreeUtil.getParentOfType(editorFile.findElementAt(caret.getOffset()), PsiMethod.class));
            }
            if (!methods.isEmpty()) {
              return collectTestMembers(methods.toArray(PsiElement.EMPTY_ARRAY), checkAbstract, checkIsTest, processor, classes);
            }
          }
          else {
            element = editorFile.findElementAt(editor.getCaretModel().getOffset());

            SelectionModel selectionModel = editor.getSelectionModel();
            if (selectionModel.hasSelection()) {
              int selectionStart = selectionModel.getSelectionStart();
              PsiClass psiClass = PsiTreeUtil.getParentOfType(editorFile.findElementAt(selectionStart), PsiClass.class);
              if (psiClass != null) {
                TextRange selectionRange = new TextRange(selectionStart, selectionModel.getSelectionEnd());
                PsiMethod[] methodsInSelection = Arrays.stream(psiClass.getMethods())
                  .filter(method -> {
                    TextRange methodTextRange = method.getTextRange();
                    return methodTextRange != null && selectionRange.contains(methodTextRange);
                  })
                  .toArray(PsiMethod[]::new);
                if (methodsInSelection.length > 0) {
                  return collectTestMembers(methodsInSelection, checkAbstract, checkIsTest, processor, classes);
                }
              }
            }
          }
        }
      }

      if (element == null) {
        element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
      }

      final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (files != null) {
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project != null) {
          final PsiManager psiManager = PsiManager.getInstance(project);
          for (VirtualFile file : files) {
            final PsiFile psiFile = psiManager.findFile(file);
            if (psiFile instanceof PsiClassOwner) {
              PsiClass[] psiClasses = ((PsiClassOwner)psiFile).getClasses();
              if (element != null && psiClasses.length > 0) {
                for (PsiClass aClass : psiClasses) {
                  if (PsiTreeUtil.isAncestor(aClass, element, false)) {
                    psiClasses = new PsiClass[]{aClass};
                    break;
                  }
                }
              }
              collectTestMembers(psiClasses, checkAbstract, checkIsTest, processor);
              for (PsiElement psiMember : processor.getCollection()) {
                classes.add(((PsiClass)psiMember).getQualifiedName());
              }
            }
          }
          return true;
        }
      }
    }
    return false;
  }

  private boolean collectTestMembers(PsiElement[] elements,
                                     boolean checkAbstract,
                                     boolean checkIsTest,
                                     PsiElementProcessor.CollectElements<PsiElement> processor, LinkedHashSet<String> classes) {
    collectTestMembers(elements, checkAbstract, checkIsTest, processor);
    for (PsiElement psiClass : processor.getCollection()) {
      classes.add(getQName(psiClass));
    }
    return classes.size() > 1;
  }

  protected PsiElement[] collectLocationElements(LinkedHashSet<String> classes, DataContext dataContext) {
    final Location<?>[] locations = Location.DATA_KEYS.getData(dataContext);
    if (locations != null) {
      List<PsiElement> elements = new ArrayList<>();
      for (Location<?> location : locations) {
        final PsiElement psiElement = location.getPsiElement();
        if (psiElement instanceof PsiNamedElement) {
          String qName = getQName(psiElement, location);
          if (qName != null) {
            classes.add(qName);
            elements.add(psiElement);
          }
        }
      }
      return elements.toArray(PsiElement.EMPTY_ARRAY);
    }
    return null;
  }

  public String getQName(PsiElement psiMember) {
    return getQName(psiMember, null);
  }

  public String getQName(PsiElement psiMember, Location location) {
    if (psiMember instanceof PsiClass) {
      return ClassUtil.getJVMClassName((PsiClass)psiMember);
    }
    else if (psiMember instanceof PsiMember) {
      final PsiClass containingClass = location instanceof MethodLocation
                                       ? ((MethodLocation)location).getContainingClass()
                                       : location instanceof PsiMemberParameterizedLocation ? ((PsiMemberParameterizedLocation)location)
                                         .getContainingClass()
                                                                                            : ((PsiMember)psiMember).getContainingClass();
      assert containingClass != null;
      return ClassUtil.getJVMClassName(containingClass) + "," + getMethodPresentation((PsiMember)psiMember);
    }
    else if (psiMember instanceof PsiPackage) {
      return ((PsiPackage)psiMember).getQualifiedName() + ".*";
    }
    return null;
  }

  protected String getMethodPresentation(PsiMember psiMember) {
    return psiMember.getName();
  }

  public boolean isMultipleElementsSelected(ConfigurationContext context) {
    if (!context.containsMultipleSelection()) return false;
    final DataContext dataContext = context.getDataContext();
    if (TestsUIUtil.isMultipleSelectionImpossible(dataContext)) return false;
    final LinkedHashSet<String> classes = new LinkedHashSet<>();
    final PsiElementProcessor.CollectElementsWithLimit<PsiElement> processor = new PsiElementProcessor.CollectElementsWithLimit<>(2);
    final PsiElement[] locationElements = collectLocationElements(classes, dataContext);
    if (locationElements != null) {
      collectTestMembers(locationElements, false, false, processor);
    }
    else {
      collectContextElements(dataContext, false, false, classes, processor);
    }
    return processor.getCollection().size() > 1;
  }

  public void setupConfigurationParamName(T configuration, Location contextLocation) {
    if (contextLocation instanceof PsiMemberParameterizedLocation) {
      final String paramSetName = ((PsiMemberParameterizedLocation)contextLocation).getParamSetName();
      if (paramSetName != null) {
        configuration.setProgramParameters(configuration.prepareParameterizedParameter(paramSetName));
      }
    }
  }
}
