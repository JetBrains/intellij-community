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

import java.util.Collection;

public abstract class CompilerReferenceService extends AbstractProjectComponent {
  public static final RegistryValue IS_ENABLED_KEY = Registry.get("bytecode.ref.index");

  protected CompilerReferenceService(Project project) {
    super(project);
  }

  public static CompilerReferenceService getInstance(@NotNull Project project) {
    return project.getComponent(CompilerReferenceService.class);
  }

  @Nullable
  public abstract GlobalSearchScope getScopeWithoutCodeReferences(@NotNull PsiElement element, @NotNull CompilerSearchAdapter adapter);

  @Nullable
  public abstract <T extends PsiNamedElement> CompilerDirectInheritorInfo<T> getDirectInheritors(@NotNull PsiNamedElement aClass,
                                                                                                 @NotNull GlobalSearchScope useScope,
                                                                                                 @NotNull GlobalSearchScope searchScope,
                                                                                                 @NotNull CompilerSearchAdapter compilerSearchAdapter,
                                                                                                 @NotNull CompilerDirectInheritorSearchAdapter<T> inheritorSearchAdapter,
                                                                                                 @NotNull FileType... searchFileTypes);


  public static boolean isEnabled() {
    return IS_ENABLED_KEY.asBoolean();
  }

  public static class CompilerDirectInheritorInfo<T extends PsiNamedElement> {
    private final Collection<T> myDirectInheritors;
    private final Collection<T> myDirectInheritorCandidates;
    private final GlobalSearchScope myDirtyScope;

    CompilerDirectInheritorInfo(Collection<T> directInheritors,
                                Collection<T> directInheritorCandidates,
                                GlobalSearchScope dirtyScope) {
      myDirectInheritors = directInheritors;
      myDirectInheritorCandidates = directInheritorCandidates;
      myDirtyScope = dirtyScope;
    }

    @NotNull
    public Collection<T> getDirectInheritors() {
      return myDirectInheritors;
    }

    @NotNull
    public Collection<T> getDirectInheritorCandidates() {
      return myDirectInheritorCandidates;
    }

    @NotNull
    public GlobalSearchScope getDirtyScope() {
      return myDirtyScope;
    }
  }
}
