// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class PsiShortNamesCacheImpl extends PsiShortNamesCache {
  @NotNull
  private final Project myProject;

  public PsiShortNamesCacheImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull PsiFile @NotNull [] getFilesByName(@NotNull String name) {
    return FilenameIndex.getFilesByName(myProject, name, GlobalSearchScope.projectScope(myProject));
  }

  @Override
  public @NotNull String @NotNull [] getAllFileNames() {
    return FilenameIndex.getAllFilenames(myProject);
  }

  @Override
  public @NotNull PsiClass @NotNull [] getClassesByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Collection<PsiClass> classes = JavaShortClassNameIndex.getInstance().getClasses(name, myProject, scope);
    if (classes.isEmpty()) return PsiClass.EMPTY_ARRAY;

    List<PsiClass> result = new ArrayList<>(classes.size());
    Map<String, List<PsiClass>> uniqueQName2Classes = new HashMap<>(classes.size());
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
              if (hiddenClassesToRemove == null) {
                hiddenClassesToRemove = new HashSet<>();
              }
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
  public @NotNull String @NotNull [] getAllClassNames() {
    return ArrayUtilRt.toStringArray(JavaShortClassNameIndex.getInstance().getAllKeys(myProject));
  }

  @Override
  public boolean processAllClassNames(@NotNull Processor<? super String> processor) {
    return JavaShortClassNameIndex.getInstance().processAllKeys(myProject, processor);
  }

  @Override
  public boolean processAllClassNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, IdFilter filter) {
    return StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.CLASS_SHORT_NAMES, processor, scope, filter);
  }

  @Override
  public boolean processAllMethodNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, IdFilter filter) {
    return StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.METHODS, processor, scope, filter);
  }

  @Override
  public boolean processAllFieldNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, IdFilter filter) {
    return StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.FIELDS, processor, scope, filter);
  }

  @Override
  public @NotNull PsiMethod @NotNull [] getMethodsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Collection<PsiMethod> methods = JavaMethodNameIndex.getInstance().getMethods(name, myProject, scope);
    return filterMembers(methods, scope, PsiMethod.EMPTY_ARRAY);
  }

  @Override
  public @NotNull PsiMethod @NotNull [] getMethodsByNameIfNotMoreThan(@NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    List<PsiMethod> methods = new SmartList<>();
    Processor<PsiMethod> processor = new CommonProcessors.CollectProcessor<>(methods) {
      @Override
      public boolean process(PsiMethod method) {
        return methods.size() != maxCount && super.process(method);
      }
    };
    StubIndex.getInstance().processElements(JavaStubIndexKeys.METHODS, name, myProject, scope, PsiMethod.class, processor);
    return filterMembers(methods, scope, PsiMethod.EMPTY_ARRAY);
  }

  @Override
  public boolean processMethodsWithName(@NotNull String name, @NotNull GlobalSearchScope scope, @NotNull Processor<? super PsiMethod> processor) {
    return StubIndex.getInstance().processElements(JavaStubIndexKeys.METHODS, name, myProject, scope, PsiMethod.class, processor);
  }

  @Override
  public @NotNull String @NotNull [] getAllMethodNames() {
    return ArrayUtilRt.toStringArray(JavaMethodNameIndex.getInstance().getAllKeys(myProject));
  }

  @Override
  public @NotNull PsiField @NotNull [] getFieldsByNameIfNotMoreThan(@NotNull String name, @NotNull GlobalSearchScope scope, int maxCount) {
    List<PsiField> fields = new SmartList<>();
    Processor<PsiField> processor = new CommonProcessors.CollectProcessor<>(fields) {
      @Override
      public boolean process(PsiField method) {
        return fields.size() != maxCount && super.process(method);
      }
    };
    StubIndex.getInstance().processElements(JavaStubIndexKeys.FIELDS, name, myProject, scope, PsiField.class, processor);
    return filterMembers(fields, scope, PsiField.EMPTY_ARRAY);
  }

  @Override
  public @NotNull PsiField @NotNull [] getFieldsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    Collection<PsiField> fields = JavaFieldNameIndex.getInstance().getFields(name, myProject, scope);
    return filterMembers(fields, scope, PsiField.EMPTY_ARRAY);
  }

  @Override
  public @NotNull String @NotNull [] getAllFieldNames() {
    return ArrayUtilRt.toStringArray(JavaFieldNameIndex.getInstance().getAllKeys(myProject));
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

  private <T extends PsiMember> @NotNull T @NotNull [] filterMembers(@NotNull Collection<? extends @NotNull T> members, @NotNull GlobalSearchScope scope, T @NotNull [] emptyArray) {
    if (members.isEmpty()) {
      return emptyArray;
    }

    PsiManager myManager = PsiManager.getInstance(myProject);
    Set<PsiMember> set = CollectionFactory.createCustomHashingStrategySet(new HashingStrategy<>() {
      @Override
      public int hashCode(PsiMember member) {
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