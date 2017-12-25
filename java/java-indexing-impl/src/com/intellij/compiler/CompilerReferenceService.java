// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The service is intended to provide an information about class/method/field usages or classes hierarchy that is obtained on compilation time.
 * It means that this service should not affect any find usages result when initial project is not compiled or project language is not support
 * by {@link LanguageCompilerRefAdapter} extension. Any result provided by this service should be valid even if some part of a given project was
 * modified after compilation.
 */
public interface CompilerReferenceService extends ProjectComponent {
  RegistryValue IS_ENABLED_KEY = Registry.get("compiler.ref.index");

  static CompilerReferenceService getInstance(@NotNull Project project) {
    return project.getComponent(CompilerReferenceService.class);
  }

  /**
   * @return a scope where given element has no references in code. This scope might be not a strict scope where element is not occurred.
   */
  @Nullable
  GlobalSearchScope getScopeWithoutCodeReferences(@NotNull PsiElement element);

  @Nullable
  GlobalSearchScope getScopeWithoutImplicitToStringCodeReferences(@NotNull PsiElement aClass);

  /**
   * @return a hierarchy of direct inheritors built on compilation time.
   * This hierarchy is restricted by searchFileType and searchScope.
   */
  @Nullable
  CompilerDirectHierarchyInfo getDirectInheritors(@NotNull PsiNamedElement aClass,
                                                  @NotNull GlobalSearchScope searchScope,
                                                  @NotNull FileType searchFileType);


  /**
   * @return a stream of functional expressions that implement given functional interface.
   * This hierarchy is restricted by searchFileType and searchScope.
   */
  @Nullable
  CompilerDirectHierarchyInfo getFunExpressions(@NotNull PsiNamedElement functionalInterface,
                                                @NotNull GlobalSearchScope searchScope,
                                                @NotNull FileType searchFileType);

  /**
   * @return count of references that were observed on compile-time (in the last compilation) or null if given element is not supported for some reason.
   */
  @Nullable
  Integer getCompileTimeOccurrenceCount(@NotNull PsiElement element, boolean isConstructorCompletion);

  boolean isActive();

  static boolean isEnabled() {
    return IS_ENABLED_KEY.asBoolean();
  }
}
