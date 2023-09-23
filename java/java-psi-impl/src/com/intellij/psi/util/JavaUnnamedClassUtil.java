// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiUnnamedClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaUnnamedClassUtil {
  public static boolean isFileWithUnnamedClass(@NotNull PsiFile file) {
    if (file instanceof PsiJavaFile) {
      PsiJavaFile javaFile = (PsiJavaFile)file;
      if (getUnnamedClassFor(javaFile) != null) return true;
    }
    return false;
  }

  @Nullable
  public static PsiUnnamedClass getUnnamedClassFor(PsiJavaFile javaFile) {
    PsiClass[] classes = javaFile.getClasses();
    if (classes.length == 1 && classes[0] instanceof PsiUnnamedClass) {
      return (PsiUnnamedClass)classes[0];
    }
    return null;
  }

  public static String trimJavaExtension(String name) {
    return StringUtil.trimEnd(name, ".java", true);
  }
}
