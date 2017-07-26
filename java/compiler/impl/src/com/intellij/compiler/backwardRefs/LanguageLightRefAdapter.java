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
package com.intellij.compiler.backwardRefs;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.backwardRefs.LightRef;
import org.jetbrains.backwardRefs.NameEnumerator;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * An interface to provide connection between compact internal representation of indexed elements and PSI
 */
public interface LanguageLightRefAdapter  {
  LanguageLightRefAdapter[] INSTANCES = new LanguageLightRefAdapter[]{new JavaLightUsageAdapter()};

  @Nullable
  static LanguageLightRefAdapter findAdapter(@NotNull VirtualFile file) {
    final FileType fileType = file.getFileType();
    return findAdapter(fileType);
  }

  @Nullable
  static LanguageLightRefAdapter findAdapter(@NotNull FileType fileType) {
    for (LanguageLightRefAdapter adapter : INSTANCES) {
      if (adapter.getFileTypes().contains(fileType)) {
        return adapter;
      }
    }
    return null;
  }

  @Nullable
  static LanguageLightRefAdapter findAdapter(@NotNull PsiElement element) {
    final VirtualFile file = PsiUtilCore.getVirtualFile(element);
    return file == null ? null : findAdapter(file);
  }

  @NotNull
  Set<FileType> getFileTypes();

  /**
   * @param element PSI element written in corresponding language
   * @param names enumerator to encode string names
   * @return
   */
  @Nullable
  LightRef asLightUsage(@NotNull PsiElement element, @NotNull NameEnumerator names) throws IOException;

  /**
   * @return "hierarchy" of given element inside the libraries scope.
   */
  @NotNull
  List<LightRef> getHierarchyRestrictedToLibraryScope(@NotNull LightRef baseRef,
                                                      @NotNull PsiElement basePsi,
                                                      @NotNull NameEnumerator names,
                                                      @NotNull GlobalSearchScope libraryScope) throws IOException;

  /**
   * class in java, class or object in some other jvm languages. used in direct inheritor search. This class object will be used to filter
   * inheritors of corresponding language among of other inheritors.
   *
   * name of this LightUsage is always enumerated internal string name of language object, eg.: A$1$B
   */
  @NotNull
  Class<? extends LightRef.LightClassHierarchyElementDef> getHierarchyObjectClass();

  /**
   * functional expression: lambda or method reference. used in functional expression search
   *
   * name of this LightUsage is always order-index inside source-code file
   */
  @NotNull
  Class<? extends LightRef> getFunExprClass();

  /**
   * @return classes that can be inheritors of given superClass. This method shouldn't directly check are
   * found elements really inheritors.
   */
  @NotNull
  PsiElement[] findDirectInheritorCandidatesInFile(@NotNull SearchId[] internalNames,
                                                   @NotNull PsiFileWithStubSupport file);

  /**
   * @param indices - ordinal-numbers (corresponding to compiler tree index visitor) of required functional expressions.
   * @return functional expressions for given functional type. Should return
   */
  @NotNull
  PsiElement[] findFunExpressionsInFile(@NotNull SearchId[] indices,
                                        @NotNull PsiFileWithStubSupport file);

  boolean isClass(@NotNull PsiElement element);

  @NotNull
  PsiElement[] getInstantiableConstructors(@NotNull PsiElement aClass);

  boolean isDirectInheritor(PsiElement candidate, PsiNamedElement baseClass);
}
