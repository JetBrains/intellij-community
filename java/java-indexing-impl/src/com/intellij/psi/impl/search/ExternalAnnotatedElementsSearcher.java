// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ReadableExternalAnnotationsManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.ExternalAnnotationsIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extends {@link AnnotatedElementsSearch} to find elements annotated via external annotations ({@code annotations.xml}).
 * <p>
 * Uses {@link ExternalAnnotationsIndex} to efficiently look up which items carry a given annotation,
 * then resolves item external names back to PSI elements.
 */
public final class ExternalAnnotatedElementsSearcher implements QueryExecutor<PsiModifierListOwner, AnnotatedElementsSearch.Parameters> {
  @Override
  public boolean execute(AnnotatedElementsSearch.@NotNull Parameters queryParameters,
                         @NotNull Processor<? super PsiModifierListOwner> consumer) {
    // isApproximate is not applicable here: the index lookup is keyed by exact annotation FQN,
    // so results are always precise and there is nothing to short-circuit.
    SearchScope searchScope = queryParameters.getScope();
    if (!(searchScope instanceof GlobalSearchScope scope)) {
      // External annotations are only indexed at a global level; local scopes are not supported.
      return true;
    }

    PsiClass annClass = queryParameters.getAnnotationClass();
    Project project = annClass.getProject();
    String annotationFQN = ReadAction.computeBlocking(annClass::getQualifiedName);
    if (annotationFQN == null) return true;

    ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    if (!(annotationsManager instanceof ReadableExternalAnnotationsManager readableAnnotationsManager)) {
      return true;
    }
    GlobalSearchScope indexQueryScope = new GlobalSearchScope(project) {
      @Override
      public boolean isSearchInModuleContent(@NotNull Module aModule) { return true; }
      @Override
      public boolean isSearchInLibraries() { return true; }
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        return readableAnnotationsManager.isUnderAnnotationRoot(file);
      }
    }.intersectWith(scope);

    return ReadAction.computeBlocking(() -> {
      return ExternalAnnotationsIndex.processItemsByAnnotation(annotationFQN, indexQueryScope, itemName -> {
        PsiModifierListOwner resolved = resolveByExternalName(itemName, project, scope);
        if (resolved == null) return true;
        // Skip elements already source/bytecode-annotated: AnnotatedElementsSearcher covers those,
        // and returning them again here would produce duplicates in the combined query result.
        PsiModifierList modList = resolved.getModifierList();
        if (modList != null && modList.hasAnnotation(annotationFQN)) return true;
        if (!AnnotatedElementsSearcher.isInstanceof(resolved, queryParameters.getTypes())) return true;
        return consumer.process(resolved);
      });
    });
  }

  /**
   * Resolves a {@link PsiModifierListOwner} from its external name as produced by
   * {@link PsiFormatUtil#getExternalName(PsiModifierListOwner, boolean) getExternalName(owner, false)}.
   * <p>
   * This is the reverse of {@link PsiFormatUtil#getExternalName}: given a string in the format used by
   * {@code annotations.xml} external annotation files, returns the corresponding PSI element
   * within the given scope.
   * <p>
   * Supported formats:
   * <ul>
   *   <li>Class: {@code com.example.Foo} or inner class {@code com.example.Outer$Inner}</li>
   *   <li>Method: {@code com.example.Foo void myMethod(java.lang.String)}</li>
   *   <li>Constructor: {@code com.example.Foo Foo(java.lang.String)}</li>
   *   <li>Field: {@code com.example.Foo myField}</li>
   *   <li>Parameter: {@code com.example.Foo void myMethod(java.lang.String) 0}</li>
   * </ul>
   *
   * @param externalName the external name string (as written in {@code annotations.xml})
   * @param project      the project
   * @param scope        the scope used to resolve the owning class
   * @return the resolved element, or {@code null} if not found within {@code scope}
   * @see PsiFormatUtil#getExternalName(PsiModifierListOwner, boolean)
   */
  private static @Nullable PsiModifierListOwner resolveByExternalName(@NotNull String externalName,
                                                                     @NotNull Project project,
                                                                     @NotNull GlobalSearchScope scope) {
    int spaceIdx = externalName.indexOf(' ');
    String classFQN = spaceIdx < 0 ? externalName : externalName.substring(0, spaceIdx);
    PsiClass psiClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), classFQN, null, false, scope);
    if (psiClass == null) return null;

    if (spaceIdx < 0 || externalName.substring(spaceIdx + 1).startsWith("<")) {
      return psiClass;
    }

    for (PsiMethod method : psiClass.getMethods()) {
      if (externalName.equals(PsiFormatUtil.getExternalName(method, false))) return method;
      for (PsiParameter param : method.getParameterList().getParameters()) {
        if (externalName.equals(PsiFormatUtil.getExternalName(param, false))) return param;
      }
    }
    for (PsiMethod constructor : psiClass.getConstructors()) {
      if (externalName.equals(PsiFormatUtil.getExternalName(constructor, false))) return constructor;
      for (PsiParameter param : constructor.getParameterList().getParameters()) {
        if (externalName.equals(PsiFormatUtil.getExternalName(param, false))) return param;
      }
    }
    for (PsiField field : psiClass.getFields()) {
      if (externalName.equals(PsiFormatUtil.getExternalName(field, false))) return field;
    }
    return null;
  }
}
