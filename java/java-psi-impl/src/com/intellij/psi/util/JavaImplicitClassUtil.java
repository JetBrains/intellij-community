// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaImplicitClassUtil {
  public static boolean isFileWithImplicitClass(@NotNull PsiFile file) {
    if (file instanceof PsiJavaFile) {
      PsiJavaFile javaFile = (PsiJavaFile)file;
      if (getImplicitClassFor(javaFile) != null) return true;
    }
    return false;
  }

  /**
   * Retrieves the implicitly declared class PSI element from the given PsiFile.
   *
   * @param file the PsiFile from which to retrieve the implicitly declared class
   * @return the implicitly declared class if found, null otherwise
   */
  @Nullable
  public static PsiImplicitClass getImplicitClassFor(@NotNull PsiFile file) {
    if (file instanceof PsiJavaFile) {
      final PsiJavaFile javaFile = (PsiJavaFile)file;
      PsiClass[] classes = javaFile.getClasses();
      if (classes.length == 1 && classes[0] instanceof PsiImplicitClass) {
        return (PsiImplicitClass)classes[0];
      }
    }
    return null;
  }

  /**
   * @param name the name of the implicitly declared class (might include the ".java" extension)
   * @return the JVM name of the implicitly declared class
   */
  public static String getJvmName(@NotNull String name) {
    return StringUtil.trimEnd(name, ".java", true);
  }
}
