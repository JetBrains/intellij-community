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
package com.intellij.compiler.backwardRefs;

import com.intellij.compiler.CompilerDirectHierarchyInfo;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.stream.Stream;

class CompilerHierarchyInfoImpl<T extends PsiElement> implements CompilerDirectHierarchyInfo<T> {
  private final GlobalSearchScope myDirtyScope;
  private final GlobalSearchScope mySearchScope;
  private final Couple<Map<VirtualFile, T[]>> myCandidatePerFile;

  CompilerHierarchyInfoImpl(Couple<Map<VirtualFile, T[]>> candidatePerFile,
                            GlobalSearchScope dirtyScope,
                            GlobalSearchScope searchScope) {
    myCandidatePerFile = candidatePerFile;
    myDirtyScope = dirtyScope;
    mySearchScope = searchScope;
  }

  @Override
  @NotNull
  public Stream<T> getHierarchyChildren() {
    return selectClassesInScope(myCandidatePerFile.getFirst(), mySearchScope);
  }

  @Override
  @NotNull
  public Stream<T> getHierarchyChildCandidates() {
    return selectClassesInScope(myCandidatePerFile.getSecond(), mySearchScope);
  }

  @Override
  @NotNull
  public GlobalSearchScope getDirtyScope() {
    return myDirtyScope;
  }

  private static <T extends PsiElement> Stream<T> selectClassesInScope(Map<VirtualFile, T[]> classesPerFile, GlobalSearchScope searchScope) {
    return classesPerFile.entrySet().stream().filter(e -> searchScope.contains(e.getKey())).flatMap(e -> Stream.of(e.getValue()));
  }
}
