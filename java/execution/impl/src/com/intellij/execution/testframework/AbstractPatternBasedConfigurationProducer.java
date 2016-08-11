/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractPatternBasedConfigurationProducer<T extends ModuleBasedConfiguration> extends JavaRunConfigurationProducerBase<T> implements Cloneable{
  protected abstract boolean isTestClass(PsiClass psiClass);
  protected abstract boolean isTestMethod(boolean checkAbstract, PsiElement psiElement);

  public AbstractPatternBasedConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  public Module findModule(ModuleBasedConfiguration configuration, Module contextModule, Set<String> patterns) {
    return JavaExecutionUtil.findModule(contextModule, patterns, configuration.getProject(), psiClass -> isTestClass(psiClass));
  }

  public boolean isMultipleElementsSelected(ConfigurationContext context) {
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

  public boolean isConfiguredFromContext(ConfigurationContext context, Set<String> patterns) {
    final LinkedHashSet<String> classes = new LinkedHashSet<>();
    final DataContext dataContext = context.getDataContext();
    if (TestsUIUtil.isMultipleSelectionImpossible(dataContext)) {
      return false;
    }
    final PsiElement[] locationElements = collectLocationElements(classes, dataContext);
    if (locationElements == null) {
      collectContextElements(dataContext, true, false, classes, new PsiElementProcessor.CollectElements<>());
    }
    if (Comparing.equal(classes, patterns)) {
      if (patterns.size() == 1) {
        final String pattern = patterns.iterator().next();
        if (!pattern.contains(",")) {
          final PsiMethod method = PsiTreeUtil.getParentOfType(CommonDataKeys.PSI_ELEMENT.getData(dataContext), PsiMethod.class);
          return method != null && isTestMethod(false, method);
        }
      }
      return true;
    }
    return false;
  }

  public PsiElement checkPatterns(ConfigurationContext context, LinkedHashSet<String> classes) {
    PsiElement[] result;
    final DataContext dataContext = context.getDataContext();
    if (TestsUIUtil.isMultipleSelectionImpossible(dataContext)) {
      return null;
    }
    final PsiElement[] locationElements = collectLocationElements(classes, dataContext);
    PsiElementProcessor.CollectElements<PsiElement> processor = new PsiElementProcessor.CollectElements<>();
    if (locationElements != null) {
      collectTestMembers(locationElements, false, true, processor);
      result = processor.toArray();
    }
    else if (collectContextElements(dataContext, true, true, classes, processor)) {
      result = processor.toArray();
    }
    else {
      return null;
    }
    if (result.length <= 1) {
      return null;
    }
    return result[0];
  }

  public void collectTestMembers(PsiElement[] psiElements,
                                 boolean checkAbstract,
                                 boolean checkIsTest,
                                 PsiElementProcessor.CollectElements<PsiElement> collectingProcessor) {
    for (PsiElement psiElement : psiElements) {
      if (psiElement instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner)psiElement).getClasses();
        for (PsiClass aClass : classes) {
          if ((!checkIsTest && aClass.hasModifierProperty(PsiModifier.PUBLIC) || checkIsTest && isTestClass(aClass)) && 
              !collectingProcessor.execute(aClass)) {
            return;
          }
        }
      } else if (psiElement instanceof PsiClass) {
        if ((!checkIsTest && ((PsiClass)psiElement).hasModifierProperty(PsiModifier.PUBLIC) || checkIsTest && isTestClass((PsiClass)psiElement)) && 
            !collectingProcessor.execute(psiElement)) {
          return;
        }
      } else if (psiElement instanceof PsiMethod) {
        if (checkIsTest && isTestMethod(checkAbstract, psiElement) && !collectingProcessor.execute(psiElement)) {
          return;
        }
        if (!checkIsTest) {
          final PsiClass containingClass = ((PsiMethod)psiElement).getContainingClass();
          if (containingClass != null && containingClass.hasModifierProperty(PsiModifier.PUBLIC) && !collectingProcessor.execute(psiElement)) {
            return;
          }
        }
      } else if (psiElement instanceof PsiDirectory) {
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)psiElement);
        if (aPackage != null && !collectingProcessor.execute(aPackage)) {
          return;
        }
      }
    }
  }

  private boolean collectContextElements(DataContext dataContext,
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
      if (editor != null) {
        final List<Caret> allCarets = editor.getCaretModel().getAllCarets();
        if (allCarets.size() > 1) {
          final PsiFile editorFile = CommonDataKeys.PSI_FILE.getData(dataContext);
          if (editorFile != null) {
            final Set<PsiMethod> methods = new LinkedHashSet<>();
            for (Caret caret : allCarets) {
              ContainerUtil.addIfNotNull(methods, PsiTreeUtil.getParentOfType(editorFile.findElementAt(caret.getOffset()), PsiMethod.class));
            }
            if (!methods.isEmpty()) {
              return collectTestMembers(methods.toArray(PsiElement.EMPTY_ARRAY), checkAbstract, checkIsTest, processor, classes);
            }
          }
        }
      }
      final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
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
                    psiClasses = new PsiClass[] {aClass};
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

  private static PsiElement[] collectLocationElements(LinkedHashSet<String> classes, DataContext dataContext) {
    final Location<?>[] locations = Location.DATA_KEYS.getData(dataContext);
    if (locations != null) {
      List<PsiElement> elements = new ArrayList<>();
      for (Location<?> location : locations) {
        final PsiElement psiElement = location.getPsiElement();
        classes.add(getQName(psiElement, location));
        elements.add(psiElement);
      }
      return elements.toArray(new PsiElement[elements.size()]);
    }
    return null;
  }

  public static String getQName(PsiElement psiMember) {
    return getQName(psiMember, null);
  }

  public static String getQName(PsiElement psiMember, Location location) {
    if (psiMember instanceof PsiClass) {
      return ClassUtil.getJVMClassName((PsiClass)psiMember);
    }
    else if (psiMember instanceof PsiMember) {
      final PsiClass containingClass = location instanceof MethodLocation
                                       ? ((MethodLocation)location).getContainingClass()
                                       : location instanceof PsiMemberParameterizedLocation ? ((PsiMemberParameterizedLocation)location).getContainingClass() 
                                                                                            : ((PsiMember)psiMember).getContainingClass();
      assert containingClass != null;
      return ClassUtil.getJVMClassName(containingClass) + "," + ((PsiMember)psiMember).getName();
    } else if (psiMember instanceof PsiPackage) {
      return ((PsiPackage)psiMember).getQualifiedName();
    }
    assert false;
    return null;
  }
}
