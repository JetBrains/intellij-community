/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.java.stubs.index.JavaAnonymousClassBaseRefOccurenceIndex;
import com.intellij.psi.impl.java.stubs.index.JavaSuperClassNameOccurenceIndex;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author max
 */
public class JavaDirectInheritorsSearcher implements QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final DirectClassInheritorsSearch.SearchParameters p, @NotNull final Processor<PsiClass> consumer) {
    final PsiClass aClass = p.getClassToProcess();
    final PsiManagerImpl psiManager = (PsiManagerImpl)aClass.getManager();

    final SearchScope useScope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      @Override
      public SearchScope compute() {
        return aClass.getUseScope();
      }
    });

    final String qualifiedName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return aClass.getQualifiedName();
      }
    });

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName)) {
      //[pasynkov]: WTF?
      //final SearchScope scope = useScope.intersectWith(GlobalSearchScope.notScope(GlobalSearchScope.getScopeRestrictedByFileTypes(
      //    GlobalSearchScope.allScope(psiManager.getProject()), StdFileTypes.JSP, StdFileTypes.JSPX)));
      final SearchScope scope = useScope;

      return AllClassesSearch.search(scope, aClass.getProject()).forEach(new Processor<PsiClass>() {
        @Override
        public boolean process(final PsiClass psiClass) {
          if (psiClass.isInterface()) {
            return consumer.process(psiClass);
          }
          final PsiClass superClass = psiClass.getSuperClass();
          if (superClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            public String compute() {
              return superClass.getQualifiedName();
            }
          }))) {
            return consumer.process(psiClass);
          }
          return true;
        }
      });
    }

    final GlobalSearchScope scope = useScope instanceof GlobalSearchScope ? (GlobalSearchScope)useScope : new EverythingGlobalScope(psiManager.getProject());
    final String searchKey = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return aClass.getName();
      }
    });
    if (StringUtil.isEmpty(searchKey)) {
      return true;
    }

    Collection<PsiReferenceList> candidates = ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiReferenceList>>() {
      @Override
      public Collection<PsiReferenceList> compute() {
        return JavaSuperClassNameOccurenceIndex.getInstance().get(searchKey, psiManager.getProject(), scope);
      }
    });

    Map<String, List<PsiClass>> classes = new HashMap<String, List<PsiClass>>();

    for (PsiReferenceList referenceList : candidates) {
      ProgressIndicatorProvider.checkCanceled();
      final PsiClass candidate = (PsiClass)referenceList.getParent();
      if (!checkInheritance(p, aClass, candidate)) continue;

      String fqn = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
          return candidate.getQualifiedName();
        }
      });
      List<PsiClass> list = classes.get(fqn);
      if (list == null) {
        list = new ArrayList<PsiClass>();
        classes.put(fqn, list);
      }
      list.add(candidate);
    }

    for (List<PsiClass> sameNamedClasses : classes.values()) {
      if (!processSameNamedClasses(consumer, aClass, sameNamedClasses)) return false;
    }

    if (p.includeAnonymous()) {
      Collection<PsiAnonymousClass> anonymousCandidates = ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiAnonymousClass>>() {
        @Override
        public Collection<PsiAnonymousClass> compute() {
          return JavaAnonymousClassBaseRefOccurenceIndex.getInstance().get(searchKey, psiManager.getProject(), scope);
        }
      });

      for (PsiAnonymousClass candidate : anonymousCandidates) {
        ProgressIndicatorProvider.checkCanceled();
        if (!checkInheritance(p, aClass, candidate)) continue;

        if (!consumer.process(candidate)) return false;
      }

      if (aClass.isEnum()) {
        // abstract enum can be subclassed in the body
        PsiField[] fields = ApplicationManager.getApplication().runReadAction(new Computable<PsiField[]>() {
          @Override
          public PsiField[] compute() {
            return aClass.getFields();
          }
        });
        for (final PsiField field : fields) {
          if (field instanceof PsiEnumConstant) {
            PsiEnumConstantInitializer initializingClass =
              ApplicationManager.getApplication().runReadAction(new Computable<PsiEnumConstantInitializer>() {
                @Override
                public PsiEnumConstantInitializer compute() {
                  return ((PsiEnumConstant)field).getInitializingClass();
                }
              });
            if (initializingClass != null) {
              if (!consumer.process(initializingClass)) return false;
            }
          }
        }
      }
    }

    return true;
  }

  private static boolean checkInheritance(final DirectClassInheritorsSearch.SearchParameters p, final PsiClass aClass, final PsiClass candidate) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return !p.isCheckInheritance() || candidate.isInheritor(aClass, false);
      }
    });
  }

  private static boolean processSameNamedClasses(Processor<PsiClass> consumer, PsiClass aClass, List<PsiClass> sameNamedClasses) {
    // if there is a class from the same jar, prefer it
    boolean sameJarClassFound = false;

    VirtualFile jarFile = PsiUtil.getJarFile(aClass);
    if (jarFile != null) {
      for (PsiClass sameNamedClass : sameNamedClasses) {
        boolean fromSameJar = Comparing.equal(PsiUtil.getJarFile(sameNamedClass), jarFile);
        if (fromSameJar) {
          sameJarClassFound = true;
          if (!consumer.process(sameNamedClass)) return false;
        }
      }
    }

    return sameJarClassFound || ContainerUtil.process(sameNamedClasses, consumer);
  }
}
