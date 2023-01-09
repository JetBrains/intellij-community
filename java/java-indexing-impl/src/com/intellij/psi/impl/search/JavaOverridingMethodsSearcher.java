// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;

public class JavaOverridingMethodsSearcher implements QueryExecutor<PsiMethod, OverridingMethodsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final OverridingMethodsSearch.SearchParameters parameters, @NotNull final Processor<? super PsiMethod> consumer) {
    final PsiMethod method = parameters.getMethod();

    Project project = ReadAction.compute(method::getProject);
    final SearchScope searchScope = parameters.getScope();

    if (searchScope instanceof LocalSearchScope) {
      VirtualFile[] files = ((LocalSearchScope)searchScope).getVirtualFiles();
      if (isJavaOnlyScope(files)) {
        return processLocalScope((LocalSearchScope)searchScope, method, project, consumer);
      }
    }

    Iterable<PsiMethod> cached = HighlightingCaches.getInstance(project).OVERRIDING_METHODS.get(method);
    if (cached == null) {
      cached = compute(method, parameters, project);
      // for non-physical elements ignore the cache completely because non-physical elements created so often/unpredictably so I can't figure out when to clear caches in this case
      if (ReadAction.compute(method::isPhysical)) {
        HighlightingCaches.getInstance(project).OVERRIDING_METHODS.put(method, cached);
      }
    }

    for (final PsiMethod subMethod : cached) {
      ProgressManager.checkCanceled();
      if (!ReadAction.compute(() -> PsiSearchScopeUtil.isInScope(searchScope, subMethod))) {
        continue;
      }
      if (!consumer.process(subMethod)) {
        return false;
      }
      if (!parameters.isCheckDeep()) {
        return true;
      }
    }
    return true;
  }

  static boolean isJavaOnlyScope(VirtualFile @NotNull [] files) {
    return ContainerUtil.and(files, file -> FileTypeRegistry.getInstance().isFileOfType(file, JavaFileType.INSTANCE));
  }

  private static boolean processLocalScope(@NotNull LocalSearchScope searchScope,
                                           @NotNull PsiMethod method,
                                           @NotNull Project project,
                                           @NotNull final Processor<? super PsiMethod> consumer) {
    // optimisation: in case of local scope it's considered cheaper to enumerate all scope files and check if there is an inheritor there,
    // instead of traversing the (potentially huge) class hierarchy and filter out almost everything by scope.
    VirtualFile[] virtualFiles = searchScope.getVirtualFiles();
    final PsiClass methodContainingClass = ReadAction.compute(method::getContainingClass);
    if (methodContainingClass == null) return true;

    final boolean[] success = {true};
    for (VirtualFile virtualFile : virtualFiles) {
      ProgressManager.checkCanceled();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
          if (psiFile != null) {
            psiFile.accept(new JavaRecursiveElementWalkingVisitor() {
              @Override
              public void visitClass(@NotNull PsiClass candidate) {
                ProgressManager.checkCanceled();
                PsiMethod overridingMethod = candidate.isInheritor(methodContainingClass, true)
                                             ? findOverridingMethod(candidate, method, methodContainingClass) : null;
                if (overridingMethod != null && !consumer.process(overridingMethod)) {
                  success[0] = false;
                  stopWalking();
                }
                else {
                  super.visitClass(candidate);
                }
              }
            });
          }
        }
      });
    }
    return success[0];
  }

  @NotNull
  private static Iterable<PsiMethod> compute(@NotNull PsiMethod method, OverridingMethodsSearch.SearchParameters parameters, @NotNull Project project) {
    final PsiClass containingClass = ReadAction.compute(method::getContainingClass);
    assert containingClass != null;
    Collection<PsiMethod> result = new HashSet<>();
    Processor<PsiClass> inheritorsProcessor = inheritor -> {
      PsiMethod found = ReadAction.compute(() -> findOverridingMethod(inheritor, method, containingClass));
      if (found != null) {
        synchronized (result) {
          result.add(found);
        }
      }
      return true;
    };

    // use wider scope to handle public method defined in package-private class which is subclassed by public class in the same package which is subclassed by public class from another package with redefined method
    SearchScope allScope = GlobalSearchScope.allScope(project);
    boolean success = ClassInheritorsSearch.search(new ClassInheritanceSearchFromJavaOverridingMethodsParameters(containingClass, allScope, parameters))
      .allowParallelProcessing().forEach(inheritorsProcessor);
    assert success;
    return result;
  }

  public static class ClassInheritanceSearchFromJavaOverridingMethodsParameters extends ClassInheritorsSearch.SearchParameters {
    private final OverridingMethodsSearch.SearchParameters myOriginalParameters;

    public ClassInheritanceSearchFromJavaOverridingMethodsParameters(@NotNull PsiClass aClass,
                                                                     @NotNull SearchScope scope,
                                                                     OverridingMethodsSearch.SearchParameters parameters) {
      super(aClass, scope, true, true, true);
      myOriginalParameters = parameters;
    }

    public OverridingMethodsSearch.SearchParameters getOriginalParameters() {
      return myOriginalParameters;
    }
  }
  
  @Nullable
  public static PsiMethod findOverridingMethod(@NotNull PsiClass inheritor,
                                               @NotNull PsiMethod method,
                                               @NotNull PsiClass methodContainingClass) {
    String name = method.getName();
    if (inheritor.findMethodsByName(name, false).length > 0) {
      PsiMethod found = MethodSignatureUtil.findMethodBySuperSignature(inheritor, getSuperSignature(inheritor, methodContainingClass, method), false);
      if (found != null && isAcceptable(found, inheritor, method, methodContainingClass)) {
        return found;
      }
    }

    if (methodContainingClass.isInterface() && !inheritor.isInterface()) {  //check for sibling implementation
      final PsiClass superClass = inheritor.getSuperClass();
      if (superClass != null && !superClass.isInheritor(methodContainingClass, true) && superClass.findMethodsByName(name, true).length > 0) {
        MethodSignature signature = getSuperSignature(inheritor, methodContainingClass, method);
        PsiMethod derived = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(inheritor, superClass, signature, true);
        if (derived != null && isAcceptable(derived, inheritor, method, methodContainingClass)) {
          return derived;
        }
      }
    }
    return null;
  }

  @NotNull
  private static MethodSignature getSuperSignature(PsiClass inheritor, @NotNull PsiClass parentClass, PsiMethod method) {
    PsiSubstitutor substitutor = TypeConversionUtil.getMaybeSuperClassSubstitutor(parentClass, inheritor, PsiSubstitutor.EMPTY);
    // if null, we have EJB custom inheritance here and still check overriding
    return method.getSignature(substitutor != null ? substitutor : PsiSubstitutor.EMPTY);
  }


  private static boolean isAcceptable(@NotNull PsiMethod found,
                                      @NotNull PsiClass foundContainingClass,
                                      @NotNull PsiMethod method,
                                      @NotNull PsiClass methodContainingClass) {
    return !found.hasModifierProperty(PsiModifier.STATIC) &&
           (!method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) ||
            JavaPsiFacade.getInstance(found.getProject()).arePackagesTheSame(methodContainingClass, foundContainingClass));
  }
}
