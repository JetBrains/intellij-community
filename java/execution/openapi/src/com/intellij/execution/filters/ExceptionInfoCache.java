// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
public class ExceptionInfoCache {
  private final ConcurrentMap<String, ClassResolveInfo> myCache = ContainerUtil.createConcurrentSoftValueMap();
  private final Project myProject;
  private final GlobalSearchScope mySearchScope;

  /**
   * @deprecated use {@link #ExceptionInfoCache(Project, GlobalSearchScope)}
   */
  @Deprecated(forRemoval = true)
  public ExceptionInfoCache(@NotNull GlobalSearchScope searchScope) {
    this(Objects.requireNonNull(searchScope.getProject()), searchScope);
  }

  public ExceptionInfoCache(@NotNull Project project, @NotNull GlobalSearchScope searchScope) {
    myProject = project;
    mySearchScope = searchScope;
  }

  @NotNull public Project getProject() {
    return myProject;
  }

  private PsiClass @NotNull [] findClassesPreferringMyScope(String className) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myProject);
    PsiClass[] result = psiFacade.findClasses(className, mySearchScope);
    return result.length != 0 ? result : psiFacade.findClasses(className, GlobalSearchScope.allScope(myProject));
  }
  
  @NotNull
  public ClassResolveInfo resolveClassOrFile(@NotNull String className, @Nullable String fileName) {
    ClassResolveInfo info = resolveClass(className);
    
    if (info.myClasses.isEmpty() && fileName != null) {
      String id = "file://" + fileName;
      ClassResolveInfo cached = myCache.get(id);
      if (cached != null) return cached;
      // try find the file with the required name
      //todo[nik] it would be better to use FilenameIndex here to honor the scope by it isn't accessible in Open API
      PsiFile[] files = PsiShortNamesCache.getInstance(myProject).getFilesByName(fileName);
      info = ExceptionInfoCache.ClassResolveInfo.create(myProject, files);
      myCache.put(id, info);
    }
    return info;
  }

  @NotNull ClassResolveInfo resolveClass(@NotNull String className) {
    ClassResolveInfo cached = myCache.get(className);
    if (cached != null && cached.isValid()) {
      return cached;
    }

    if (DumbService.isDumb(myProject)) {
      return ClassResolveInfo.EMPTY;
    }

    PsiClass[] classes = findClassesPreferringMyScope(className);
    if (classes.length == 0) {
      final int dollarIndex = className.indexOf('$');
      if (dollarIndex >= 0) {
        classes = findClassesPreferringMyScope(className.substring(0, dollarIndex));
      }
    }

    ClassResolveInfo result = ClassResolveInfo.create(myProject, classes);
    myCache.put(className, result);
    return result;
  }

  public static class ClassResolveInfo {
    static final ClassResolveInfo EMPTY = new ClassResolveInfo(Collections.emptyMap(), false);
    
    private final Map<VirtualFile, PsiElement> myClasses;
    private final boolean myInLibrary;
    private volatile List<PsiClass> myExceptionClasses;

    ClassResolveInfo(Map<VirtualFile, PsiElement> classes, boolean library) {
      myClasses = classes;
      myInLibrary = library;
    }
    
    List<PsiClass> getExceptionClasses() {
      List<PsiClass> exceptionClasses = myExceptionClasses;
      if (exceptionClasses == null) {
        exceptionClasses = new ArrayList<>();
        for (PsiElement value : myClasses.values()) {
          PsiClass psiClass = ObjectUtils.tryCast(value, PsiClass.class);
          if (psiClass != null && InheritanceUtil.isInheritor(psiClass, CommonClassNames.JAVA_LANG_THROWABLE)) {
            exceptionClasses.add(psiClass);
          }
        }
        myExceptionClasses = exceptionClasses;
      }
      return myExceptionClasses;
    }
    
    boolean isValid() {
      return ContainerUtil.and(myClasses.values(), PsiElement::isValid) && 
             (myExceptionClasses == null || ContainerUtil.and(myExceptionClasses, PsiElement::isValid));
    }

    public Map<VirtualFile, PsiElement> getClasses() {
      return myClasses;
    }

    public boolean isInLibrary() {
      return myInLibrary;
    }

    @NotNull
    static ExceptionInfoCache.ClassResolveInfo create(Project project, PsiElement[] elements) {
      ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
      Map<VirtualFile, PsiElement> result = new LinkedHashMap<>();
      boolean library = true;
      for (PsiElement element : elements) {
        element = element.getNavigationElement();
        PsiFile file = element.getContainingFile();
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) continue;
        if (index.isInContent(virtualFile)) {
          if (library) {
            library = false;
            result.clear();
          }
        }
        else if (!library) {
          continue;
        }
        result.put(virtualFile, element);
      }
      return new ClassResolveInfo(result, library);
    }
  }

}
