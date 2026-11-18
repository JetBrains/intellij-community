// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.generate.tostring;

import com.intellij.modcommand.ActionContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension which allows to prohibit toString generation for some classes
 */
public interface GenerateToStringClassFilter {
  ExtensionPointName<GenerateToStringClassFilter> EP_NAME = ExtensionPointName.create("com.intellij.generation.toStringClassFilter");

  /**
   * @param psiClass class to check
   * @return return false if toString should not be generated for the class
   */
  @Contract(pure = true)
  boolean canGenerateToString(PsiClass psiClass);

  /**
   * @param context ActionContext to find the target class where to generate toString
   * @return a target class with respect to registered filters; null if not found
   */
  static @Nullable PsiClass findTargetClass(@NotNull ActionContext context) {
    if (!(context.file() instanceof PsiJavaFile javaFile)) return null;
    PsiClass psiClass = PsiTreeUtil.getParentOfType(context.findLeaf(), PsiClass.class, false);
    if (psiClass == null) {
      PsiClass[] classes = javaFile.getClasses();
      if (classes.length != 1 || !(classes[0] instanceof PsiImplicitClass implicitClass) ||
          implicitClass.getFirstChild() == null || !PsiMethodUtil.hasMainMethod(implicitClass)) {
        return null;
      }
      psiClass = implicitClass;
    }
    for (GenerateToStringClassFilter filter : EP_NAME.getExtensionList()) {
      if (!filter.canGenerateToString(psiClass)) return null;
    }
    return psiClass;
  }
}
