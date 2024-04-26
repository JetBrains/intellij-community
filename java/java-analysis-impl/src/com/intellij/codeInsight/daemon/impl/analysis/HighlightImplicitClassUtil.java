// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.JavaImplicitClassUtil;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Checks and reports errors for implicitly declared classes {@link PsiImplicitClass}.
 */
public final class HighlightImplicitClassUtil {

  static HighlightInfo.@Nullable Builder checkImplicitClassHasMainMethod(@NotNull PsiJavaFile file) {
    if (!PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, file)) return null;
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
    if (!PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, file)) return null;
    PsiImplicitClass implicitClass = JavaImplicitClassUtil.getImplicitClassFor(file);
    if (implicitClass == null) return null;
    String name = implicitClass.getQualifiedName();
    if (!PsiNameHelper.getInstance(file.getProject()).isIdentifier(name)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(file)
        .fileLevelAnnotation()
        .description(JavaErrorBundle.message("error.implicit.class.has.invalid.file.name"));
    }
    return null;
  }

  static HighlightInfo.@Nullable Builder checkInitializersInImplicitClass(@NotNull PsiClassInitializer initializer) {
    if (initializer.getContainingClass() instanceof PsiImplicitClass && PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, initializer)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(initializer)
        .descriptionAndTooltip(JavaErrorBundle.message("error.initializers.are.not.allowed.in.implicit.classes"))
        .registerFix(QuickFixFactory.getInstance().createDeleteFix(initializer), null, null, null, null);
    }
    return null;
  }

  static HighlightInfo.@Nullable Builder checkPackageNotAllowedInImplicitClass(@NotNull PsiPackageStatement statement,
                                                                               @NotNull PsiFile file) {
    if (PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, file) && JavaImplicitClassUtil.isFileWithImplicitClass(file)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(statement)
        .descriptionAndTooltip(JavaErrorBundle.message("error.package.statement.not.allowed.for.implicit.class"))
        .registerFix(QuickFixFactory.getInstance().createDeleteFix(statement), null, null, null, null);
    }
    return null;
  }

  @Nullable
  static HighlightInfo.Builder checkDuplicateClasses(@NotNull PsiJavaFile file) {
    if (!PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, file)) return null;
    PsiImplicitClass implicitClass = JavaImplicitClassUtil.getImplicitClassFor(file);
    if (implicitClass == null) return null;

    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) return null;

    GlobalSearchScope scope = GlobalSearchScope.moduleScope(module).intersectWith(implicitClass.getResolveScope());
    String qualifiedName = implicitClass.getQualifiedName();
    if (qualifiedName == null) {
      return null;
    }
    PsiClass[] classes = JavaPsiFacade.getInstance(implicitClass.getProject()).findClasses(qualifiedName, scope);
    return HighlightClassUtil.checkDuplicateClasses(implicitClass, classes);
  }
}
