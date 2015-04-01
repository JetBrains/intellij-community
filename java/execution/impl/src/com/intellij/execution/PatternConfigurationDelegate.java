/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.junit.JavaRunConfigurationProducerBase;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public abstract class PatternConfigurationDelegate<T extends ModuleBasedConfiguration> extends JavaRunConfigurationProducerBase<T> implements Cloneable{
  protected abstract boolean isTestClass(PsiClass psiClass);
  protected abstract boolean isTestMethod(boolean checkAbstract, PsiElement psiElement);

  public PatternConfigurationDelegate(ConfigurationType configurationType) {
    super(configurationType);
  }

  public Module findModule(ModuleBasedConfiguration configuration, Module contextModule, Set<String> patterns) {
    return JavaExecutionUtil.findModule(contextModule, patterns, configuration.getProject(), new Condition<PsiClass>() {
      @Override
      public boolean value(PsiClass psiClass) {
        return isTestClass(psiClass);
      }
    });
  }

  public boolean isMultipleElementsSelected(ConfigurationContext context) {
    if (TestsUIUtil.isMultipleSelectionImpossible(context.getDataContext())) return false;
    final LinkedHashSet<String> classes = new LinkedHashSet<String>();
    final PsiElement[] elements = collectPatternElements(context, classes);
    if (elements != null && collectTestMembers(elements, false).size() > 1) {
      return true;
    }
    return false;
  }

  public boolean isConfiguredFromContext(ConfigurationContext context, Set<String> patterns) {
    final LinkedHashSet<String> classes = new LinkedHashSet<String>();
    collectPatternElements(context, classes);
    if (Comparing.equal(classes, patterns)) {
      return true;
    }
    return false;
  }

  public PsiElement checkPatterns(ConfigurationContext context, LinkedHashSet<String> classes) {
    PsiElement[] elements = collectPatternElements(context, classes);
    if (elements == null || collectTestMembers(elements, false).size() <= 1) {
      return null;
    }
    return elements[0];
  }

  public Set<PsiElement> collectTestMembers(PsiElement[] psiElements, boolean checkAbstract) {
    final Set<PsiElement> foundMembers = new LinkedHashSet<PsiElement>();
    for (PsiElement psiElement : psiElements) {
      if (psiElement instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner)psiElement).getClasses();
        for (PsiClass aClass : classes) {
          if (isTestClass(aClass)) {
            foundMembers.add(aClass);
          }
        }
      } else if (psiElement instanceof PsiClass) {
        if (isTestClass((PsiClass)psiElement)) {
          foundMembers.add(psiElement);
        }
      } else if (psiElement instanceof PsiMethod) {
        if (isTestMethod(checkAbstract, psiElement)) {
          foundMembers.add(psiElement);
        }
      } else if (psiElement instanceof PsiDirectory) {
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)psiElement);
        if (aPackage != null) {
          foundMembers.add(aPackage);
        }
      }
    }
    return foundMembers;
  }
  
  private PsiElement[] collectPatternElements(ConfigurationContext context, LinkedHashSet<String> classes) {
    final DataContext dataContext = context.getDataContext();
    final Location<?>[] locations = Location.DATA_KEYS.getData(dataContext);
    if (locations != null) {
      List<PsiElement> elements = new ArrayList<PsiElement>();
      for (Location<?> location : locations) {
        final PsiElement psiElement = location.getPsiElement();
        classes.add(getQName(psiElement, location));
        elements.add(psiElement);
      }
      return elements.toArray(new PsiElement[elements.size()]);
    }
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (elements != null) {
      for (PsiElement psiClass : collectTestMembers(elements, true)) {
        classes.add(getQName(psiClass));
      }
      return elements;
    } else {
      final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (files != null) {
        final List<PsiFile> psiFiles = new ArrayList<PsiFile>();
        final PsiManager psiManager = PsiManager.getInstance(context.getProject());
        for (VirtualFile file : files) {
          final PsiFile psiFile = psiManager.findFile(file);
          if (psiFile instanceof PsiClassOwner) {
            for (PsiElement psiMember : collectTestMembers(((PsiClassOwner)psiFile).getClasses(), true)) {
              classes.add(((PsiClass)psiMember).getQualifiedName());
            }
            psiFiles.add(psiFile);
          }
        }
        return psiFiles.toArray(new PsiElement[psiFiles.size()]);
      }
    }
    return null;
  }

  public static String getQName(PsiElement psiMember) {
    return getQName(psiMember, null);
  }

  public static String getQName(PsiElement psiMember, Location location) {
    if (psiMember instanceof PsiClass) {
      return ((PsiClass)psiMember).getQualifiedName();
    }
    else if (psiMember instanceof PsiMember) {
      final PsiClass containingClass = location instanceof MethodLocation
                                       ? ((MethodLocation)location).getContainingClass(): ((PsiMember)psiMember).getContainingClass();
      assert containingClass != null;
      return containingClass.getQualifiedName() + "," + ((PsiMember)psiMember).getName();
    } else if (psiMember instanceof PsiPackage) {
      return ((PsiPackage)psiMember).getQualifiedName();
    }
    assert false;
    return null;
  }
}
