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
package com.intellij.compiler;

import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class JavaBaseCompilerSearchAdapter implements CompilerSearchAdapter {
  public static final JavaBaseCompilerSearchAdapter INSTANCE = new JavaBaseCompilerSearchAdapter();

  @Override
  public boolean needOverrideElement() {
    return true;
  }

  @Nullable
  @Override
  public CompilerElement asCompilerElement(@NotNull PsiElement element) {
    if (mayBeVisibleOutsideOwnerFile(element)) {
      if (element instanceof PsiField) {
        final PsiField field = (PsiField)element;
        final PsiClass aClass = field.getContainingClass();
        if (aClass == null || aClass instanceof PsiAnonymousClass) return null;
        final String jvmOwnerName = ClassUtil.getJVMClassName(aClass);
        final String name = field.getName();
        if (name == null || jvmOwnerName == null) return null;
        return new CompilerElement.CompilerField(jvmOwnerName, name);
      }
      else if (element instanceof PsiMethod) {
        final PsiClass aClass = ((PsiMethod)element).getContainingClass();
        if (aClass == null || aClass instanceof PsiAnonymousClass) return null;
        final String jvmOwnerName = ClassUtil.getJVMClassName(aClass);
        if (jvmOwnerName == null) return null;
        final PsiMethod method = (PsiMethod)element;
        final String name = method.isConstructor() ? "<init>" : method.getName();
        final int parametersCount = method.getParameterList().getParametersCount();
        return new CompilerElement.CompilerMethod(jvmOwnerName, name, parametersCount);
      }
      else if (element instanceof PsiClass) {
        final String jvmClassName = ClassUtil.getJVMClassName((PsiClass)element);
        if (jvmClassName != null) {
          return new CompilerElement.CompilerClass(jvmClassName);
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public CompilerElement[] libraryElementAsCompilerElements(@NotNull PsiElement element) {
    if (mayBeVisibleOutsideOwnerFile(element)) {
      if (element instanceof PsiField || element instanceof PsiMethod) {
        final String name = ((PsiMember)element).getName();

        final Function<String, CompilerElement> builder;
        if (element instanceof PsiField) {
          builder = (ownerJvmName) -> new CompilerElement.CompilerField(ownerJvmName, name);
        }
        else {
          final int parametersCount = ((PsiMethod)element).getParameterList().getParametersCount();
          builder = (ownerJvmName) -> new CompilerElement.CompilerMethod(ownerJvmName, name, parametersCount);
        }

        final List<CompilerElement> result = new ArrayList<>();
        inLibrariesHierarchy(((PsiMember)element).getContainingClass(), aClass -> {
          final String jvmClassName = ClassUtil.getJVMClassName(aClass);
          if (jvmClassName != null) {
            result.add(builder.apply(jvmClassName));
          }
          return true;
        });
        return result.toArray(new CompilerElement[result.size()]);
      }
      else if (element instanceof PsiClass) {
        final List<CompilerElement> result = new ArrayList<>();
        inLibrariesHierarchy((PsiClass)element, aClass -> {
          final String jvmClassName = ClassUtil.getJVMClassName(aClass);
          if (jvmClassName != null) {
            result.add(new CompilerElement.CompilerClass(jvmClassName));
          }
          return true;
        });
        return result.toArray(new CompilerElement[result.size()]);
      }
    }
    return CompilerElement.EMPTY_ARRAY;
  }

  private static boolean mayBeVisibleOutsideOwnerFile(@NotNull PsiElement element) {
    if (!(element instanceof PsiModifierListOwner)) return true;
    if (((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PRIVATE)) return false;
    return true;
  }

  private static void inLibrariesHierarchy(PsiClass aClass, Processor<PsiClass> processor) {
    if (aClass != null) {
      processor.process(aClass);
      ClassInheritorsSearch.search(aClass, LibraryScopeCache.getInstance(aClass.getProject()).getLibrariesOnlyScope(), true)
        .forEach(processor);
    }
  }
}
