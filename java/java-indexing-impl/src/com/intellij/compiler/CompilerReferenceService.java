/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
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

public abstract class CompilerReferenceService extends AbstractProjectComponent {
  public static final RegistryValue IS_ENABLED_KEY = Registry.get("compiler.ref.index");
  public static volatile boolean enabledInTests = false;

  protected CompilerReferenceService(Project project) {
    super(project);
  }

  public static CompilerReferenceService getInstance(@NotNull Project project) {
    return project.getComponent(CompilerReferenceService.class);
  }

  @Nullable
  public abstract GlobalSearchScope getScopeWithoutCodeReferences(@NotNull PsiElement element);

  @Nullable
  public abstract CompilerDirectHierarchyInfo getDirectInheritors(@NotNull PsiNamedElement aClass,
                                                                  @NotNull GlobalSearchScope useScope,
                                                                  @NotNull GlobalSearchScope searchScope,
                                                                  @NotNull FileType searchFileType);

  @Nullable
  public abstract CompilerDirectHierarchyInfo getFunExpressions(@NotNull PsiNamedElement functionalInterface,
                                                                @NotNull GlobalSearchScope useScope,
                                                                @NotNull GlobalSearchScope searchScope,
                                                                @NotNull FileType searchFileType);

  public static boolean isEnabled() {
    return (!ApplicationManager.getApplication().isHeadlessEnvironment() || enabledInTests) && IS_ENABLED_KEY.asBoolean();
  }
}
