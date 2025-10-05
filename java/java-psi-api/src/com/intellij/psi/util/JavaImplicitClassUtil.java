// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class providing methods to handle implicit classes in Java-related PSI structures.
 */
public final class JavaImplicitClassUtil {

  /**
   * Checks whether the given PSI element represents a file that contains an implicit class and
   * a file is a compact source file.
   *
   * @param file the PSI element to be checked, typically a PsiFile or its derivative.
   * @return {@code true} if the file contains an implicit class, {@code false} otherwise.
   */
  public static boolean isFileWithImplicitClass(@NotNull PsiElement file) {
    return getImplicitClassFor(file) != null;
  }

  /**
   * Retrieves the implicitly declared class PSI element from the given PsiFile.
   *
   * @param file the PsiFile from which to retrieve the implicitly declared class
   * @return the implicitly declared class if found, null otherwise
   */
  public static @Nullable PsiImplicitClass getImplicitClassFor(@NotNull PsiElement file) {
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
