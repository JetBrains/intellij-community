// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.util;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.reverse;

public class JvmClassUtil {

  private JvmClassUtil() {}

  @Nullable
  public static String getJvmClassName(@NotNull JvmClass aClass) {
    final List<String> parts = new SmartList<>();

    JvmClass current = aClass;
    while (true) {
      final JvmClass containingClass = current.getContainingClass();
      if (containingClass == null) { // current class is top-level class
        String qualifiedName = current.getQualifiedName();
        if (qualifiedName == null) return null;
        parts.add(qualifiedName);
        break;
      }
      else {
        String name = current.getName();
        if (name == null) return null;
        parts.add(name);
        current = containingClass;
      }
    }

    return join(reverse(parts), "$");
  }

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
