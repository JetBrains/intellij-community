/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.io.File;

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

  @NotNull
  public abstract File getIndexDir();

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
                                                                  @NotNull GlobalSearchScope useScope,
                                                                  @NotNull GlobalSearchScope searchScope,
                                                                  @NotNull FileType searchFileType);


  /**
   * @return a stream of functional expressions that implement given functional interface.
   * This hierarchy is restricted by searchFileType and searchScope.
   */
  @Nullable
  public abstract CompilerDirectHierarchyInfo getFunExpressions(@NotNull PsiNamedElement functionalInterface,
                                                                @NotNull GlobalSearchScope useScope,
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
