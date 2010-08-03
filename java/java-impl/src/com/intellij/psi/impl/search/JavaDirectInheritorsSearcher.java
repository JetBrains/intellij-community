/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
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
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author max
 */
public class JavaDirectInheritorsSearcher implements QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  public boolean execute(final DirectClassInheritorsSearch.SearchParameters p, final Processor<PsiClass> consumer) {
    final PsiClass aClass = p.getClassToProcess();
    final PsiManagerImpl psiManager = (PsiManagerImpl)PsiManager.getInstance(aClass.getProject());

    final SearchScope useScope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      public SearchScope compute() {
        return aClass.getUseScope();
      }
    });

    final String qualifiedName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return aClass.getQualifiedName();
      }
    });

    if (CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName)) {
      final SearchScope scope = useScope.intersectWith(GlobalSearchScope.notScope(GlobalSearchScope.getScopeRestrictedByFileTypes(
          GlobalSearchScope.allScope(psiManager.getProject()), StdFileTypes.JSP, StdFileTypes.JSPX)));

      return AllClassesSearch.search(scope, aClass.getProject()).forEach(new Processor<PsiClass>() {
        public boolean process(final PsiClass psiClass) {
          if (psiClass.isInterface()) {
            return consumer.process(psiClass);
          }
          final PsiClass superClass = psiClass.getSuperClass();
          if (superClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
            return consumer.process(psiClass);
          }
          return true;
        }
      });
    }

    final GlobalSearchScope scope = useScope instanceof GlobalSearchScope ? (GlobalSearchScope)useScope : new EverythingGlobalScope(psiManager.getProject());
    final String searchKey = aClass.getName();
    if (StringUtil.isEmpty(searchKey)) {
      return true;
    }

    Collection<PsiReferenceList> candidates = ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiReferenceList>>() {
      public Collection<PsiReferenceList> compute() {
        return JavaSuperClassNameOccurenceIndex.getInstance().get(searchKey, psiManager.getProject(), scope);
      }
    });

    Map<String, List<PsiClass>> classes = new HashMap<String, List<PsiClass>>();

    for (PsiReferenceList referenceList : candidates) {
      ProgressManager.checkCanceled();
      PsiClass candidate = (PsiClass)referenceList.getParent();
      String fqn = candidate.getQualifiedName();
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
        public Collection<PsiAnonymousClass> compute() {
          return JavaAnonymousClassBaseRefOccurenceIndex.getInstance().get(searchKey, psiManager.getProject(), scope);
        }
      });

      for (PsiAnonymousClass candidate : anonymousCandidates) {
        ProgressManager.checkCanceled();
        if (!consumer.process(candidate)) return false;
      }

      if (aClass.isEnum()) {
        // abstract enum can be subclassed in the body
        PsiField[] fields = ApplicationManager.getApplication().runReadAction(new Computable<PsiField[]>() {
          public PsiField[] compute() {
            return aClass.getFields();
          }
        });
        for (final PsiField field : fields) {
          if (field instanceof PsiEnumConstant) {
            PsiEnumConstantInitializer initializingClass =
              ApplicationManager.getApplication().runReadAction(new Computable<PsiEnumConstantInitializer>() {
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

  private static boolean processSameNamedClasses(Processor<PsiClass> consumer, PsiClass aClass, List<PsiClass> sameNamedClasses) {
    // if there is a class from the same jar, prefer it
    boolean sameJarClassFound = false;
    for (PsiClass sameNamedClass : sameNamedClasses) {
      boolean fromSameJar = isFromTheSameJar(sameNamedClass, aClass);
      if (fromSameJar) {
        sameJarClassFound = true;
        if (!consumer.process(sameNamedClass)) return false;
      }
    }

    if (!sameJarClassFound) {
      for (PsiClass sameNamedClass : sameNamedClasses) {
        if (!consumer.process(sameNamedClass)) return false;
      }
    }
    return true;
  }

  private static VirtualFile getJarFile(PsiElement candidate) {
    VirtualFile file = candidate.getContainingFile().getVirtualFile();
    if (file != null && file.getFileSystem() instanceof JarFileSystem) {
      return JarFileSystem.getInstance().getVirtualFileForJar(file);
    }
    return file;
  }
  public static boolean isFromTheSameJar(PsiElement candidate, PsiElement other) {
    VirtualFile c1 = getJarFile(candidate);
    VirtualFile c2 = getJarFile(other);
    return c1 != null && c1 == c2;
  }

}
