// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractJavaTestConfigurationProducer<T extends JavaTestConfigurationBase> extends JavaRunConfigurationProducerBase<T> {
  /**
   * @deprecated Override {@link #getConfigurationFactory()}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
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
  
  protected boolean hasDetectedTestFramework(PsiClass psiClass) {
    return getCurrentFramework(psiClass) != null;
  }

  protected boolean isApplicableTestType(String type, ConfigurationContext context) {
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull T configuration, @NotNull ConfigurationContext context) {
    if (isMultipleElementsSelected(context)) {
      return false;
    }
    if (!isApplicableTestType(configuration.getTestType(), context)) return false;
    final RunConfiguration predefinedConfiguration = context.getOriginalConfiguration(getConfigurationType());
    
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

    final Location contextLocation = context.getLocation();
    if (contextLocation == null) {
      return false;
    }
    if (differentParamSet(configuration, contextLocation)) return false;

    Location location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) {
      return false;
    }
    
    if (isConfiguredByElement(configuration, context, location.getPsiElement())) {
      final Module configurationModule = configuration.getConfigurationModule().getModule();
      final Module locationModule = location.getModule();
      if (Comparing.equal(locationModule, configurationModule)) return true;
      if ((predefinedModule != null || locationModule == null) && Comparing.equal(predefinedModule, configurationModule)) return true;
    }

    return false;
  }

  protected boolean isConfiguredByElement(@NotNull T configuration,
                                          @NotNull ConfigurationContext context,
                                          @NotNull PsiElement element) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass != null && !hasDetectedTestFramework(psiClass)) {
      return false;
    }

    return configuration.isConfiguredByElement(element);
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
        psiElement = PsiTreeUtil.getNonStrictParentOfType(psiElement, PsiMember.class, PsiClassOwner.class);
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
                                           LinkedHashSet<? super String> classes,
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
                String qName = ((PsiClass)psiMember).getQualifiedName();
                if (qName != null) {
                  classes.add(qName);
                }
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
                                     PsiElementProcessor.CollectElements<PsiElement> processor,
                                     LinkedHashSet<? super String> classes) {
    collectTestMembers(elements, checkAbstract, checkIsTest, processor);
    for (PsiElement psiClass : processor.getCollection()) {
      String qName = getQName(psiClass);
      if (qName != null) {
        classes.add(qName);
      }
    }
    return classes.size() > 1;
  }

  protected PsiElement[] collectLocationElements(LinkedHashSet<? super String> classes, DataContext dataContext) {
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

  @Nullable
  public String getQName(PsiElement psiMember) {
    return getQName(psiMember, null);
  }

  @Nullable
  public String getQName(PsiElement psiMember, @Nullable Location location) {
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

  @Nullable
  public static PsiPackage checkPackage(final PsiElement element) {
    if (element == null || !element.isValid()) return null;
    final Project project = element.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (element instanceof PsiPackage) {
      final PsiPackage aPackage = (PsiPackage)element;
      final PsiDirectory[] directories = aPackage.getDirectories(GlobalSearchScope.projectScope(project));
      for (final PsiDirectory directory : directories) {
        if (isSource(directory, fileIndex)) return aPackage;
      }
      return null;
    }
    else if (element instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory)element;
      if (isSource(directory, fileIndex)) {
        return JavaDirectoryService.getInstance().getPackage(directory);
      }
      else {
        final VirtualFile virtualFile = directory.getVirtualFile();
        //choose default package when selection on content root
        if (virtualFile.equals(fileIndex.getContentRootForFile(virtualFile))) {
          final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
          if (module != null) {
            for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
              if (virtualFile.equals(entry.getFile())) {
                final SourceFolder[] folders = entry.getSourceFolders();
                Set<String> packagePrefixes = new HashSet<>();
                for (SourceFolder folder : folders) {
                  packagePrefixes.add(folder.getPackagePrefix());
                }
                if (packagePrefixes.size() > 1) return null;
                return JavaPsiFacade.getInstance(project).findPackage(packagePrefixes.isEmpty() ? "" : packagePrefixes.iterator().next());
              }
            }
          }
        }
        return null;
      }
    }
    else {
      return null;
    }
  }

  private static boolean isSource(final PsiDirectory directory, final ProjectFileIndex fileIndex) {
    final VirtualFile virtualFile = directory.getVirtualFile();
    return fileIndex.getSourceRootForFile(virtualFile) != null;
  }
}
