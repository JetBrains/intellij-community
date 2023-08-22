// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiSuperMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class JavaOverridingMethodUtil {
  private static final int MAX_OVERRIDDEN_METHOD_SEARCH = 20;

  /**
   * The method allows to search of overriding methods in "cheap enough" manner if it's possible.
   *
   * @param preFilter should filter out non-interesting methods (eg: methods non-annotated with some '@Override' annotation).
   *                  It must not perform resolve, index queries or any other heavyweight operation since in the worst case all methods with name
   *                  as the source method name will be processed.
   *
   * @return null if it's expensive to search for overriding methods in given case, otherwise returns stream of overriding methods for given preFilter
   */
  @Nullable
  public static Stream<PsiMethod> getOverridingMethodsIfCheapEnough(@NotNull PsiMethod method,
                                                                    @Nullable GlobalSearchScope searchScope,
                                                                    @NotNull Predicate<? super PsiMethod> preFilter) {
    if (!isInSourceContent(method)) return null;
    Project project = method.getProject();
    String name = method.getName();
    SearchScope useScope = method.getUseScope();
    GlobalSearchScope effectiveSearchScope = GlobalSearchScopeUtil.toGlobalSearchScope(useScope, project);
    if (searchScope != null) {
      effectiveSearchScope = effectiveSearchScope.intersectWith(searchScope);
    }

    List<PsiMethod> methods = new ArrayList<>();
    if (!StubIndex.getInstance().processElements(JavaStubIndexKeys.METHODS,
                                                name,
                                                project,
                                                new JavaSourceFilterScope(effectiveSearchScope),
                                                PsiMethod.class,
                                            m -> {
                                              ProgressManager.checkCanceled();
                                              if (m == method) return true;
                                              if (!preFilter.test(m)) return true;
                                              methods.add(m);
                                              return methods.size() <= MAX_OVERRIDDEN_METHOD_SEARCH;
                                            })) {
      return null;
    }

    return methods.stream().filter(candidate -> PsiSuperMethodUtil.isSuperMethod(candidate, method));
  }

  public static boolean containsAnnotationWithName(@NotNull PsiModifierListOwner modifierListOwner, @NotNull String shortAnnotationName) {
    PsiModifierList list = modifierListOwner.getModifierList();
    if (list != null) {
      for (PsiAnnotation annotation : list.getAnnotations()) {
        PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
        if (ref != null && shortAnnotationName.equals(ref.getReferenceName())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isInSourceContent(@NotNull PsiElement e) {
    final VirtualFile file = e.getContainingFile().getVirtualFile();
    if (file == null) return false;
    final ProjectFileIndex index = ProjectRootManager.getInstance(e.getProject()).getFileIndex();
    return index.isInContent(file) || index.isInLibrary(file);
  }
}
