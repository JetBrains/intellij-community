// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.JavaImplicitClassUtil;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Checks and reports errors for implicitly declared classes {@link PsiImplicitClass}.
 */
public final class HighlightImplicitClassUtil {

  static HighlightInfo.@Nullable Builder checkImplicitClassHasMainMethod(@NotNull PsiJavaFile file) {
    if (!HighlightingFeature.IMPLICIT_CLASSES.isAvailable(file)) return null;
    PsiImplicitClass implicitClass = JavaImplicitClassUtil.getImplicitClassFor(file);
    if (implicitClass == null) return null;
    PsiMethod[] methods = implicitClass.getMethods();
    boolean hasMainMethod = ContainerUtil.exists(methods, method -> "main".equals(method.getName()) && PsiMethodUtil.isMainMethod(method));
    if (!hasMainMethod) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                     .range(file)
                     .fileLevelAnnotation()
                     .registerFix(QuickFixFactory.getInstance().createAddMainMethodFix(implicitClass), null, null, null, null)
                     .description(JavaErrorBundle.message("error.implicit.class.contains.no.main.method"));
    }
    return null;
  }

  static HighlightInfo.@Nullable Builder checkImplicitClassFileIsValidIdentifier(@NotNull PsiJavaFile file) {
    if (!HighlightingFeature.IMPLICIT_CLASSES.isAvailable(file)) return null;
    PsiImplicitClass implicitClass = JavaImplicitClassUtil.getImplicitClassFor(file);
    if (implicitClass == null) return null;
    String name = ClassUtil.getJVMClassName(implicitClass);
    if (!PsiNameHelper.getInstance(file.getProject()).isQualifiedName(name)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(file)
        .fileLevelAnnotation()
        .description(JavaErrorBundle.message("error.implicit.class.has.invalid.file.name"));
    }
    return null;
  }

  static HighlightInfo.@Nullable Builder checkInitializersInImplicitClass(@NotNull PsiClassInitializer initializer) {
    if (initializer.getContainingClass() instanceof PsiImplicitClass && HighlightingFeature.IMPLICIT_CLASSES.isAvailable(initializer)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(initializer).descriptionAndTooltip(
        JavaErrorBundle.message("error.initializers.are.not.allowed.in.implicit.classes"));
    }
    return null;
  }

  static HighlightInfo.@Nullable Builder checkPackageNotAllowedInImplicitClass(@NotNull PsiPackageStatement statement,
                                                                               @NotNull PsiFile file) {
    if (HighlightingFeature.IMPLICIT_CLASSES.isAvailable(file) && JavaImplicitClassUtil.isFileWithImplicitClass(file)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(statement).descriptionAndTooltip(
        JavaErrorBundle.message("error.package.statement.not.allowed.for.implicit.class"));
    }
    return null;
  }
}
