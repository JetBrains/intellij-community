// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.util;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class JvmClassUtil {

  private JvmClassUtil() {}

  @Contract(pure = true)
  @NotNull
  public static Comparator<JvmClass> createScopeComparator(@NotNull GlobalSearchScope scope) {
    return (c1, c2) -> {
      VirtualFile file1 = PsiUtilCore.getVirtualFile(c1.getSourceElement());
      VirtualFile file2 = PsiUtilCore.getVirtualFile(c2.getSourceElement());
      if (file1 == null) return file2 == null ? 0 : -1;
      if (file2 == null) return 1;
      return scope.compare(file2, file1);
    };
  }
}
