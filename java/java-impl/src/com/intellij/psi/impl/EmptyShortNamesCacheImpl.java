/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class EmptyShortNamesCacheImpl extends PsiShortNamesCache {

  @Override
  @NotNull
  public PsiClass[] getClassesByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String[] getAllClassNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void getAllClassNames(@NotNull HashSet<String> dest) {
    // do nothing
  }

  @Override
  @NotNull
  public PsiMethod[] getMethodsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull final String name, @NotNull final GlobalSearchScope scope, final int maxCount) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String[] getAllMethodNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void getAllMethodNames(@NotNull HashSet<String> set) {
    // do nothing
  }

  @Override
  @NotNull
  public PsiField[] getFieldsByName(@NotNull String name, @NotNull GlobalSearchScope scope) {
    return PsiField.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String[] getAllFieldNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void getAllFieldNames(@NotNull HashSet<String> set) {
    // do nothing
  }
}