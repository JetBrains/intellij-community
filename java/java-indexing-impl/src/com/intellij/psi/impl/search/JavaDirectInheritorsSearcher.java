/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnonymousClassBaseRefOccurenceIndex;
import com.intellij.psi.impl.java.stubs.index.JavaSuperClassNameOccurenceIndex;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
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

    final Project project = PsiUtilCore.getProjectInReadAction(aClass);
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName)) {
      //[pasynkov]: WTF?
      //final SearchScope scope = useScope.intersectWith(GlobalSearchScope.notScope(GlobalSearchScope.getScopeRestrictedByFileTypes(
      //    GlobalSearchScope.allScope(psiManager.getProject()), StdFileTypes.JSP, StdFileTypes.JSPX)));

      return AllClassesSearch.search(useScope, project).forEach(new Processor<PsiClass>() {
        @Override
        public boolean process(final PsiClass psiClass) {
          ProgressManager.checkCanceled();
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

    final GlobalSearchScope scope = useScope instanceof GlobalSearchScope ? (GlobalSearchScope)useScope : new EverythingGlobalScope(project);
    final String searchKey = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return aClass.getName();
      }
    });
    if (StringUtil.isEmpty(searchKey)) {
      return true;
    }

    Collection<PsiReferenceList> candidates = MethodUsagesSearcher.resolveInReadAction(project,
                                                                                       new Computable<Collection<PsiReferenceList>>() {
                                                                                         @Override
                                                                                         public Collection<PsiReferenceList> compute() {
                                                                                           return JavaSuperClassNameOccurenceIndex
                                                                                             .getInstance().get(searchKey, project, scope);
                                                                                         }
                                                                                       });

    Map<String, List<PsiClass>> classes = new HashMap<String, List<PsiClass>>();

    for (final PsiReferenceList referenceList : candidates) {
      ProgressManager.checkCanceled();
      final PsiClass candidate = (PsiClass)ApplicationManager.getApplication().runReadAction(new Computable<PsiElement>() {
        @Override
        public PsiElement compute() {
          return referenceList.getParent();
        }
      });
      if (!checkInheritance(p, aClass, candidate, project)) continue;

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

    if (!classes.isEmpty()) {
      final VirtualFile jarFile = getJarFile(aClass);
      for (List<PsiClass> sameNamedClasses : classes.values()) {
        ProgressManager.checkCanceled();
        if (!processSameNamedClasses(consumer, sameNamedClasses, jarFile)) return false;
      }
    }

    if (p.includeAnonymous()) {
      Collection<PsiAnonymousClass> anonymousCandidates = MethodUsagesSearcher.resolveInReadAction(project,
                                                                                                   new Computable<Collection<PsiAnonymousClass>>() {
                                                                                                     @Override
                                                                                                     public Collection<PsiAnonymousClass> compute() {
                                                                                                       return JavaAnonymousClassBaseRefOccurenceIndex
                                                                                                         .getInstance()
                                                                                                         .get(searchKey, project, scope);
                                                                                                     }
                                                                                                   });

      for (PsiAnonymousClass candidate : anonymousCandidates) {
        ProgressManager.checkCanceled();
        if (!checkInheritance(p, aClass, candidate, project)) continue;

        if (!consumer.process(candidate)) return false;
      }

      boolean isEnum = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          return aClass.isEnum();
        }
      });
      if (isEnum) {
        // abstract enum can be subclassed in the body
        PsiField[] fields = ApplicationManager.getApplication().runReadAction(new Computable<PsiField[]>() {
          @Override
          public PsiField[] compute() {
            return aClass.getFields();
          }
        });
        for (final PsiField field : fields) {
          ProgressManager.checkCanceled();
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

  private static boolean checkInheritance(final DirectClassInheritorsSearch.SearchParameters p, final PsiClass aClass, final PsiClass candidate, Project project) {
    return MethodUsagesSearcher.resolveInReadAction(project, new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return !p.isCheckInheritance() || candidate.isInheritor(aClass, false);
      }
    });
  }

  private static boolean processSameNamedClasses(Processor<PsiClass> consumer, List<PsiClass> sameNamedClasses, final VirtualFile jarFile) {
    // if there is a class from the same jar, prefer it
    boolean sameJarClassFound = false;

    if (jarFile != null && sameNamedClasses.size() > 1) {
      for (PsiClass sameNamedClass : sameNamedClasses) {
        ProgressManager.checkCanceled();
        boolean fromSameJar = Comparing.equal(getJarFile(sameNamedClass), jarFile);
        if (fromSameJar) {
          sameJarClassFound = true;
          if (!consumer.process(sameNamedClass)) return false;
        }
      }
    }

    return sameJarClassFound || ContainerUtil.process(sameNamedClasses, consumer);
  }

  private static VirtualFile getJarFile(final PsiClass aClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return PsiUtil.getJarFile(aClass);
      }
    });
  }
}
