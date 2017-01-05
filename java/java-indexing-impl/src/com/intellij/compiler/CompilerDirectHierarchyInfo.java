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

import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

/**
 * represents search result for functional expressions or inheritance hierarchy of given interface/class using indices built on compilation time.
 */
public interface CompilerDirectHierarchyInfo {
  /**
   * Can be used as direct hierarchy children without explicit inheritance verification
   */
  @NotNull
  Stream<PsiElement> getHierarchyChildren();

  /**
   * A scope where compiler based index search was not performed
   */
  @NotNull
  GlobalSearchScope getDirtyScope();
}
