/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.compiler;

import com.intellij.openapi.components.AbstractProjectComponent;
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
 * by {@link LanguageLightRefAdapter} extension. Any result provided by this service should be valid even if some part of a given project was
 * modified after compilation.
 */
public abstract class CompilerReferenceService extends AbstractProjectComponent {
  public static final RegistryValue IS_ENABLED_KEY = Registry.get("compiler.ref.index");

  protected CompilerReferenceService(Project project) {
    super(project);
  }

  public static CompilerReferenceService getInstance(@NotNull Project project) {
    return project.getComponent(CompilerReferenceService.class);
  }

  /**
   * @return a scope where given element has no references in code. This scope might be not a strict scope where element is not occurred.
   */
  @Nullable
  public abstract GlobalSearchScope getScopeWithoutCodeReferences(@NotNull PsiElement element);

  /**
   * @return a hierarchy of direct inheritors built on compilation time.
   * This hierarchy is restricted by searchFileType and searchScope.
   */
  @Nullable
  public abstract CompilerDirectHierarchyInfo getDirectInheritors(@NotNull PsiNamedElement aClass,
                                                                  @NotNull GlobalSearchScope searchScope,
                                                                  @NotNull FileType searchFileType);


  /**
   * @return a stream of functional expressions that implement given functional interface.
   * This hierarchy is restricted by searchFileType and searchScope.
   */
  @Nullable
  public abstract CompilerDirectHierarchyInfo getFunExpressions(@NotNull PsiNamedElement functionalInterface,
                                                                @NotNull GlobalSearchScope searchScope,
                                                                @NotNull FileType searchFileType);

  /**
   * @return count of references that were observed on compile-time (in the last compilation) or null if given element is not supported for some reason.
   */
  @Nullable
  public abstract Integer getCompileTimeOccurrenceCount(@NotNull PsiElement element, boolean isConstructorCompletion);

  public abstract boolean isActive();

  public static boolean isEnabled() {
    return IS_ENABLED_KEY.asBoolean();
  }
}
