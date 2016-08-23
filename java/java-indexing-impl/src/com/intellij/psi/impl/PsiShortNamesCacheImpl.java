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
package com.intellij.psi.impl;

import com.intellij.openapi.progress.ProgressIndicatorProvider;
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
import com.intellij.util.*;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.IdFilter;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiShortNamesCacheImpl extends PsiShortNamesCache {
  private final PsiManagerEx myManager;

  public PsiShortNamesCacheImpl(PsiManagerEx manager) {
    myManager = manager;
  }

  @Override
  @NotNull
  public PsiFile[] getFilesByName(@NotNull String name) {
    return FilenameIndex.getFilesByName(myManager.getProject(), name, GlobalSearchScope.projectScope(myManager.getProject()));
  }

  @Override
  @NotNull
  public String[] getAllFileNames() {
    return FilenameIndex.getAllFilenames(myManager.getProject());
  }

  @Override
  @NotNull
  public PsiClass[] getClassesByName(@NotNull String name, @NotNull final GlobalSearchScope scope) {
    final Collection<PsiClass> classes = JavaShortClassNameIndex.getInstance().get(name, myManager.getProject(), scope);

    if (classes.isEmpty()) return PsiClass.EMPTY_ARRAY;
    ArrayList<PsiClass> list = new ArrayList<>(classes.size());
    Map<String, List<PsiClass>> uniqueQName2Classes = new THashMap<>(classes.size());
    Set<PsiClass> hiddenClassesToRemove = null;

    OuterLoop:
    for (PsiClass aClass : classes) {
      VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
      if (!scope.contains(vFile)) continue;

      String qName = aClass.getQualifiedName();
      if (qName != null) {
        List<PsiClass> previousQNamedClasses = uniqueQName2Classes.get(qName);
        List<PsiClass> qNamedClasses;

        if (previousQNamedClasses != null) {
          qNamedClasses = new SmartList<>();

          for(PsiClass previousClass:previousQNamedClasses) {
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
            } else {
              qNamedClasses.add(aClass);
            }
          }
        } else {
          qNamedClasses = new SmartList<>(aClass);
        }
        uniqueQName2Classes.put(qName, qNamedClasses);
      }
      list.add(aClass);
    }

    if (hiddenClassesToRemove != null) list.removeAll(hiddenClassesToRemove);

    return list.toArray(new PsiClass[list.size()]);
  }

  @Override
  @NotNull
  public String[] getAllClassNames() {
    return ArrayUtil.toStringArray(JavaShortClassNameIndex.getInstance().getAllKeys(myManager.getProject()));
  }

  @Override
  public void getAllClassNames(@NotNull HashSet<String> set) {
    Processor<String> processor = Processors.cancelableCollectProcessor(set);
    processAllClassNames(processor);
  }

  @Override
  public boolean processAllClassNames(Processor<String> processor) {
    return JavaShortClassNameIndex.getInstance().processAllKeys(myManager.getProject(), processor);
  }

  @Override
  public boolean processAllClassNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter) {
    return StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.CLASS_SHORT_NAMES, processor, scope, filter);
  }

  @Override
  public boolean processAllMethodNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter) {
    return StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.METHODS, processor, scope, filter);
  }

  @Override
  public boolean processAllFieldNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter) {
    return StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.FIELDS, processor, scope, filter);
  }

  @Override
  @NotNull
  public PsiMethod[] getMethodsByName(@NotNull String name, @NotNull final GlobalSearchScope scope) {
    Collection<PsiMethod> methods = StubIndex.getElements(JavaStubIndexKeys.METHODS, name, myManager.getProject(),
                                                          new JavaSourceFilterScope(scope), PsiMethod.class);
    if (methods.isEmpty()) return PsiMethod.EMPTY_ARRAY;

    List<PsiMethod> list = filterMembers(methods, scope);
    return list.toArray(new PsiMethod[list.size()]);
  }


  @Override
  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull final String name, @NotNull final GlobalSearchScope scope, final int maxCount) {
    final List<PsiMethod> methods = new SmartList<>();
    StubIndex.getInstance().processElements(JavaStubIndexKeys.METHODS, name, myManager.getProject(), scope, PsiMethod.class, new
                                            CommonProcessors.CollectProcessor < PsiMethod > (methods){
    @Override
      public boolean process(PsiMethod method) {
        return methods.size() != maxCount && super.process(method);
      }
    });
    if (methods.isEmpty()) return PsiMethod.EMPTY_ARRAY;

    List<PsiMethod> list = filterMembers(methods, scope);
    return list.toArray(new PsiMethod[list.size()]);
  }

  @Override
  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull GlobalSearchScope scope,
                                        @NotNull Processor<PsiMethod> processor) {
    return StubIndex.getInstance().processElements(JavaStubIndexKeys.METHODS, name, myManager.getProject(), scope, PsiMethod.class, processor);
  }

  @Override
  @NotNull
  public String[] getAllMethodNames() {
    return ArrayUtil.toStringArray(JavaMethodNameIndex.getInstance().getAllKeys(myManager.getProject()));
  }

  @Override
  public void getAllMethodNames(@NotNull HashSet<String> set) {
    JavaMethodNameIndex.getInstance().processAllKeys(myManager.getProject(), Processors.cancelableCollectProcessor(set));
  }

  @Override
  @NotNull
  public PsiField[] getFieldsByNameIfNotMoreThan(@NotNull String name, @NotNull final GlobalSearchScope scope, final int maxCount) {
    final List<PsiField> methods = new SmartList<>();
    StubIndex.getInstance().processElements(JavaStubIndexKeys.FIELDS, name, myManager.getProject(), scope, PsiField.class, new
                                            CommonProcessors.CollectProcessor < PsiField > (methods){
    @Override
      public boolean process(PsiField method) {
        return methods.size() != maxCount && super.process(method);
      }
    });
    if (methods.isEmpty()) return PsiField.EMPTY_ARRAY;

    List<PsiField> list = filterMembers(methods, scope);
    return list.toArray(new PsiField[list.size()]);
  }

  @NotNull
  @Override
  public PsiField[] getFieldsByName(@NotNull String name, @NotNull final GlobalSearchScope scope) {
    final Collection<PsiField> fields = JavaFieldNameIndex.getInstance().get(name, myManager.getProject(), scope);

    if (fields.isEmpty()) return PsiField.EMPTY_ARRAY;

    List<PsiField> list = filterMembers(fields, scope);
    return list.toArray(new PsiField[list.size()]);
  }

  @Override
  @NotNull
  public String[] getAllFieldNames() {
    return ArrayUtil.toStringArray(JavaFieldNameIndex.getInstance().getAllKeys(myManager.getProject()));
  }

  @Override
  public void getAllFieldNames(@NotNull HashSet<String> set) {
    Processor<String> processor = Processors.cancelableCollectProcessor(set);
    JavaFieldNameIndex.getInstance().processAllKeys(myManager.getProject(), processor);
  }

  @Override
  public boolean processFieldsWithName(@NotNull String name,
                                       @NotNull Processor<? super PsiField> processor,
                                       @NotNull GlobalSearchScope scope,
                                       @Nullable IdFilter filter) {
    return StubIndex.getInstance().processElements(JavaStubIndexKeys.FIELDS, name, myManager.getProject(), new JavaSourceFilterScope(scope),
                                                   filter, PsiField.class, processor);
  }

  @Override
  public boolean processMethodsWithName(@NonNls @NotNull String name,
                                        @NotNull Processor<? super PsiMethod> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    return StubIndex.getInstance().processElements(JavaStubIndexKeys.METHODS, name, myManager.getProject(),
                                                   new JavaSourceFilterScope(scope), filter, PsiMethod.class, processor);
  }

  @Override
  public boolean processClassesWithName(@NotNull String name,
                                        @NotNull Processor<? super PsiClass> processor,
                                        @NotNull GlobalSearchScope scope,
                                        @Nullable IdFilter filter) {
    return StubIndex.getInstance().processElements(JavaStubIndexKeys.CLASS_SHORT_NAMES, name, myManager.getProject(),
                                                   new JavaSourceFilterScope(scope), filter, PsiClass.class, processor);
  }

  private <T extends PsiMember> List<T> filterMembers(Collection<T> members, final GlobalSearchScope scope) {
    List<T> result = new ArrayList<>(members.size());
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

    for (T member : members) {
      ProgressIndicatorProvider.checkCanceled();

      if (!scope.contains(member.getContainingFile().getVirtualFile())) continue;
      if (!set.add(member)) continue;
      result.add(member);
    }

    return result;
  }
}
