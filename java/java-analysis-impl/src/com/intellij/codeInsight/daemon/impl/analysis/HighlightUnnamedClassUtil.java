// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaUnnamedClassUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Checks and reports errors for unnamed classes {@link com.intellij.psi.PsiUnnamedClass}.
 */
public final class HighlightUnnamedClassUtil {

  static HighlightInfo.@Nullable Builder checkUnnamedClssHasMainMethod(@NotNull PsiJavaFile file) {
    if (!HighlightingFeature.UNNAMED_CLASSES.isAvailable(file)) return null;
    PsiClass[] classes = file.getClasses();
    if (classes.length != 1) return null;
    PsiClass aClass = classes[0];
    if (aClass instanceof PsiUnnamedClass unnamedClass) {
      PsiMethod[] methods = unnamedClass.getMethods();
      boolean hasMainMethod = ContainerUtil.exists(methods, method -> "main".equals(method.getName()));
      if (!hasMainMethod) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                       .range(file)
                       .fileLevelAnnotation()
                       .registerFix(QuickFixFactory.getInstance().createAddMainMethodFix(unnamedClass), null, null, null, null)
                       .description(JavaErrorBundle.message("error.unnamed.class.contains.no.main.method"));
      }
    }
    return null;
  }

  static HighlightInfo.@Nullable Builder checkInitializersInUnnamedClass(@NotNull PsiClassInitializer initializer) {
    if (initializer.getContainingClass() instanceof PsiUnnamedClass && HighlightingFeature.UNNAMED_CLASSES.isAvailable(initializer)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(initializer).descriptionAndTooltip(
        JavaErrorBundle.message("error.initializers.are.not.allowed.in.unnamed.classes"));
    }
    return null;
  }

  static HighlightInfo.@Nullable Builder checkPackageNotAllowedInUnnamedClass(@NotNull PsiPackageStatement statement,
                                                                              @NotNull PsiFile file) {
    if (HighlightingFeature.UNNAMED_CLASSES.isAvailable(file) && JavaUnnamedClassUtil.isFileWithUnnamedClass(file)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(
        JavaErrorBundle.message("error.package.statement.not.allowed.for.unnamed.class"));
    }
    return null;
  }
}
