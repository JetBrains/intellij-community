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
package com.intellij.lang.jvm.util;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class JvmClassUtil {

  @Contract(pure = true)
  @NotNull
  public static Comparator<JvmClass> createScopeComparator(@NotNull GlobalSearchScope scope) {
    return (c1, c2) -> {
      VirtualFile file1 = PsiUtilCore.getVirtualFile(c1.getPsiElement());
      VirtualFile file2 = PsiUtilCore.getVirtualFile(c2.getPsiElement());
      if (file1 == null) return file2 == null ? 0 : -1;
      if (file2 == null) return 1;
      return scope.compare(file2, file1);
    };
  }
}
