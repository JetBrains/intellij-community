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
package com.intellij.psi.impl;

import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaFieldNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaMethodNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.IdFilter;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiShortNamesCacheImpl extends PsiShortNamesCache {
  private final Project myProject;

  public PsiShortNamesCacheImpl(Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public PsiFile[] getFilesByName(@NotNull String name) {
    return FilenameIndex.getFilesByName(myProject, name, GlobalSearchScope.projectScope(myProject));
  }

  @Override
  @NotNull
  public String[] getAllFileNames() {
    return FilenameIndex.getAllFilenames(myProject);
  }

  @Override
  @NotNull
  public PsiClass[] getClassesByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Collection<PsiClass> classes = JavaShortClassNameIndex.getInstance().get(name, myProject, scope);
    if (classes.isEmpty()) return PsiClass.EMPTY_ARRAY;

    List<PsiClass> result = new ArrayList<>(classes.size());
    Map<String, List<PsiClass>> uniqueQName2Classes = new THashMap<>(classes.size());
    Set<PsiClass> hiddenClassesToRemove = null;

    OuterLoop:
    for (PsiClass aClass : classes) {
      VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
      if (!scope.contains(vFile)) continue;

      String qName = aClass.getQualifiedName();
      if (qName != null) {
        List<PsiClass> previousQNamedClasses = uniqueQName2Classes.get(qName);
        List<PsiClass> qNamedClasses = new SmartList<>();

        if (previousQNamedClasses != null) {
          for (PsiClass previousClass : previousQNamedClasses) {
            VirtualFile previousClassVFile = previousClass.getContainingFile().getVirtualFile();
            int res = scope.compare(previousClassVFile, vFile);
            if (res > 0) {
              continue OuterLoop; // previousClass hides aClass in classpath, so skip adding aClass
            }
            else if (res < 0) {
              // aClass hides previousClass in classpath, so remove it from list later
              if (hiddenClassesToRemove == null) hiddenClassesToRemove = new THashSet<>();
              hiddenClassesToRemove.add(previousClass);
              qNamedClasses.add(aClass);
            }
            else {
              qNamedClasses.add(aClass);
            }
          }
        }
        else {
          qNamedClasses.add(aClass);
        }

        uniqueQName2Classes.put(qName, qNamedClasses);
      }

      result.add(aClass);
    }

    if (hiddenClassesToRemove != null) result.removeAll(hiddenClassesToRemove);

    return result.toArray(PsiClass.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public String[] getAllClassNames() {
    return ArrayUtil.toStringArray(JavaShortClassNameIndex.getInstance().getAllKeys(myProject));
  }

  @Override
  public boolean processAllClassNames(@NotNull Processor<String> processor) {
    return JavaShortClassNameIndex.getInstance().processAllKeys(myProject, processor);
  }

  @Override
  public boolean processAllClassNames(@NotNull Processor<String> processor, @NotNull GlobalSearchScope scope, IdFilter filter) {
    return StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.CLASS_SHORT_NAMES, processor, scope, filter);
  }

  @Override
  public boolean processAllMethodNames(@NotNull Processor<String> processor, @NotNull GlobalSearchScope scope, IdFilter filter) {
    return StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.METHODS, processor, scope, filter);
  }

  @Override
  public boolean processAllFieldNames(@NotNull Processor<String> processor, @NotNull GlobalSearchScope scope, IdFilter filter) {
    return StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.FIELDS, processor, scope, filter);
  }

  @Override
  @NotNull
  public PsiMethod[] getMethodsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Collection<PsiMethod> methods = JavaMethodNameIndex.getInstance().get(name, myProject, scope);
    return filterMembers(methods, scope, PsiMethod.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    List<PsiMethod> methods = new SmartList<>();
    Processor<PsiMethod> processor = new CommonProcessors.CollectProcessor<PsiMethod>(methods) {
      @Override
      public boolean process(PsiMethod method) {
        return methods.size() != maxCount && super.process(method);
      }
    };
    StubIndex.getInstance().processElements(JavaStubIndexKeys.METHODS, name, myProject, scope, PsiMethod.class, processor);
    return filterMembers(methods, scope, PsiMethod.EMPTY_ARRAY);
  }

  @Override
  public boolean processMethodsWithName(@NotNull String name, @NotNull GlobalSearchScope scope, @NotNull Processor<PsiMethod> processor) {
    return StubIndex.getInstance().processElements(JavaStubIndexKeys.METHODS, name, myProject, scope, PsiMethod.class, processor);
  }

  @Override
  @NotNull
  public String[] getAllMethodNames() {
    return ArrayUtil.toStringArray(JavaMethodNameIndex.getInstance().getAllKeys(myProject));
  }

  @Override
  @NotNull
  public PsiField[] getFieldsByNameIfNotMoreThan(@NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    List<PsiField> fields = new SmartList<>();
    Processor<PsiField> processor = new CommonProcessors.CollectProcessor<PsiField>(fields) {
      @Override
      public boolean process(PsiField method) {
        return fields.size() != maxCount && super.process(method);
      }
    };
    StubIndex.getInstance().processElements(JavaStubIndexKeys.FIELDS, name, myProject, scope, PsiField.class, processor);
    return filterMembers(fields, scope, PsiField.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public PsiField[] getFieldsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Collection<PsiField> fields = JavaFieldNameIndex.getInstance().get(name, myProject, scope);
    return filterMembers(fields, scope, PsiField.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public String[] getAllFieldNames() {
    return ArrayUtil.toStringArray(JavaFieldNameIndex.getInstance().getAllKeys(myProject));
  }

  @Override
  public boolean processFieldsWithName(@NotNull String name,
                                       @NotNull Processor<? super PsiField> processor,
                                       @NotNull GlobalSearchScope scope,
                                       @Nullable IdFilter filter) {
    return StubIndex.getInstance().processElements(
      JavaStubIndexKeys.FIELDS, name, myProject, new JavaSourceFilterScope(scope), filter, PsiField.class, processor);
  }

  @Override
  public boolean processMethodsWithName(@NotNull String name,
                                        @NotNull Processor<? super PsiMethod> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    return StubIndex.getInstance().processElements(
      JavaStubIndexKeys.METHODS, name, myProject, new JavaSourceFilterScope(scope), filter, PsiMethod.class, processor);
  }

  @Override
  public boolean processClassesWithName(@NotNull String name,
                                        @NotNull Processor<? super PsiClass> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    return StubIndex.getInstance().processElements(
      JavaStubIndexKeys.CLASS_SHORT_NAMES, name, myProject, new JavaSourceFilterScope(scope), filter, PsiClass.class, processor);
  }

  @NotNull
  private <T extends PsiMember> T[] filterMembers(@NotNull Collection<T> members, @NotNull GlobalSearchScope scope, @NotNull T[] emptyArray) {
    if (members.isEmpty()) {
      return emptyArray;
    }

    PsiManager myManager = PsiManager.getInstance(myProject);
    Set<PsiMember> set = new THashSet<>(members.size(), new TObjectHashingStrategy<PsiMember>() {
      @Override
      public int computeHashCode(PsiMember member) {
        int code = 0;
        final PsiClass clazz = member.getContainingClass();
        if (clazz != null) {
          String name = clazz.getName();
          if (name != null) {
            code += name.hashCode();
          }
          else {
            //anonymous classes are not equivalent
            code += clazz.hashCode();
          }
        }
        if (member instanceof PsiMethod) {
          code += 37 * ((PsiMethod)member).getParameterList().getParametersCount();
        }
        return code;
      }

      @Override
      public boolean equals(PsiMember object, PsiMember object1) {
        return myManager.areElementsEquivalent(object, object1);
      }
    });

    List<T> result = new ArrayList<>(members.size());
    for (T member : members) {
      ProgressIndicatorProvider.checkCanceled();
      if (scope.contains(member.getContainingFile().getVirtualFile()) && set.add(member)) {
        result.add(member);
      }
    }
    return result.toArray(emptyArray);
  }
}