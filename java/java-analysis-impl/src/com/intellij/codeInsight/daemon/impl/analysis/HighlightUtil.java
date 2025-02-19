// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.ContainerProvider;
import com.intellij.codeInsight.JavaModuleSystemEx;
import com.intellij.codeInsight.JavaModuleSystemEx.ErrorWithFixes;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.codeserver.core.JavaPsiModifierUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// generates HighlightInfoType.ERROR-like HighlightInfos
public final class HighlightUtil {

  private HighlightUtil() { }

  public static @NotNull @NlsSafe String formatClass(@NotNull PsiClass aClass) {
    return formatClass(aClass, true);
  }

  public static @NotNull String formatClass(@NotNull PsiClass aClass, boolean fqn) {
    return PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME |
                                             PsiFormatUtilBase.SHOW_ANONYMOUS_CLASS_VERBOSE | (fqn ? PsiFormatUtilBase.SHOW_FQ_NAME : 0));
  }

  static @NotNull Pair<@Nls String, List<IntentionAction>> accessProblemDescriptionAndFixes(@NotNull PsiElement ref,
                                                                                            @NotNull PsiElement resolved,
                                                                                            @NotNull JavaResolveResult result) {
    assert resolved instanceof PsiModifierListOwner : resolved;
    PsiModifierListOwner refElement = (PsiModifierListOwner)resolved;
    String symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());

    if (refElement.hasModifierProperty(PsiModifier.PRIVATE)) {
      String containerName = getContainerName(refElement, result.getSubstitutor());
      return Pair.pair(JavaErrorBundle.message("private.symbol", symbolName, containerName), null);
    }

    if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
      String containerName = getContainerName(refElement, result.getSubstitutor());
      return Pair.pair(JavaErrorBundle.message("protected.symbol", symbolName, containerName), null);
    }

    PsiClass packageLocalClass = JavaPsiModifierUtil.getPackageLocalClassInTheMiddle(ref);
    if (packageLocalClass != null) {
      refElement = packageLocalClass;
      symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());
    }

    if (refElement.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) || packageLocalClass != null) {
      String containerName = getContainerName(refElement, result.getSubstitutor());
      return Pair.pair(JavaErrorBundle.message("package.local.symbol", symbolName, containerName), null);
    }

    String containerName = getContainerName(refElement, result.getSubstitutor());
    ErrorWithFixes problem = checkModuleAccess(resolved, ref, symbolName, containerName);
    if (problem != null) return Pair.pair(problem.message, problem.fixes);
    return Pair.pair(JavaErrorBundle.message("visibility.access.problem", symbolName, containerName), null);
  }

  static @Nullable @Nls ErrorWithFixes checkModuleAccess(@NotNull PsiElement resolved, @NotNull PsiElement ref, @NotNull JavaResolveResult result) {
    PsiElement refElement = resolved;
    PsiClass packageLocalClass = JavaPsiModifierUtil.getPackageLocalClassInTheMiddle(ref);
    if (packageLocalClass != null) {
      refElement = packageLocalClass;
    }

    String symbolName = HighlightMessageUtil.getSymbolName(refElement, result.getSubstitutor());
    String containerName = (resolved instanceof PsiModifierListOwner modifierListOwner)
                           ? getContainerName(modifierListOwner, result.getSubstitutor())
                           : null;
    return checkModuleAccess(resolved, ref, symbolName, containerName);
  }

  private static @Nullable @Nls ErrorWithFixes checkModuleAccess(@NotNull PsiElement target,
                                                                 @NotNull PsiElement place,
                                                                 @Nullable String symbolName,
                                                                 @Nullable String containerName) {
    for (JavaModuleSystem moduleSystem : JavaModuleSystem.EP_NAME.getExtensionList()) {
      if (moduleSystem instanceof JavaModuleSystemEx system) {
        if (target instanceof PsiClass targetClass) {
          final ErrorWithFixes problem = system.checkAccess(targetClass, place);
          if (problem != null) return problem;
        }
        if (target instanceof PsiPackage targetPackage) {
          final ErrorWithFixes problem = system.checkAccess(targetPackage.getQualifiedName(), null, place);
          if (problem != null) return problem;
        }
      }
      else if (!isAccessible(moduleSystem, target, place)) {
        return new ErrorWithFixes(JavaErrorBundle.message("visibility.module.access.problem", symbolName, containerName, moduleSystem.getName()));
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkModuleReferenceAccess(@NotNull PsiImportModuleStatement statement) {
    PsiJavaModuleReferenceElement refElement = statement.getModuleReference();
    if (refElement == null) return null;
    PsiJavaModuleReference ref = refElement.getReference();
    assert ref != null : refElement.getParent();
    PsiJavaModule target = ref.resolve();
    if (target == null) return null;
    for (JavaModuleSystem moduleSystem : JavaModuleSystem.EP_NAME.getExtensionList()) {
      if (!(moduleSystem instanceof JavaModuleSystemEx javaModuleSystemEx)) continue;
      ErrorWithFixes fixes = javaModuleSystemEx.checkAccess(target, statement);
      if (fixes == null) continue;
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(statement)
        .descriptionAndTooltip(fixes.message);
      fixes.fixes.forEach(fix -> info.registerFix(fix, null, null, null, null));
      return info;
    }
    return null;
  }

  private static boolean isAccessible(@NotNull JavaModuleSystem system, @NotNull PsiElement target, @NotNull PsiElement place) {
    if (target instanceof PsiClass psiClass) return system.isAccessible(psiClass, place);
    if (target instanceof PsiPackage psiPackage) return system.isAccessible(psiPackage.getQualifiedName(), null, place);
    return true;
  }

  private static PsiElement getContainer(@NotNull PsiModifierListOwner refElement) {
    for (ContainerProvider provider : ContainerProvider.EP_NAME.getExtensionList()) {
      PsiElement container = provider.getContainer(refElement);
      if (container != null) return container;
    }
    return refElement.getParent();
  }

  private static String getContainerName(@NotNull PsiModifierListOwner refElement, @NotNull PsiSubstitutor substitutor) {
    PsiElement container = getContainer(refElement);
    return container == null ? "?" : HighlightMessageUtil.getSymbolName(container, substitutor);
  }

  static HighlightInfo.Builder checkReference(@NotNull PsiJavaCodeReferenceElement ref, @NotNull JavaResolveResult result) {
    PsiElement resolved = result.getElement();

    boolean skipValidityChecks =
      ref.getParent() instanceof PsiMethodCallExpression || resolved == null ||
      PsiUtil.isInsideJavadocComment(ref) ||
      PsiTreeUtil.getParentOfType(ref, PsiPackageStatement.class, true) != null ||
      resolved instanceof PsiPackage && ref.getParent() instanceof PsiJavaCodeReferenceElement;

    if (skipValidityChecks) return null;
    
    final ErrorWithFixes moduleProblem = checkModuleAccess(resolved, ref, result);
    if (moduleProblem != null) {
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(findPackagePrefix(ref))
        .descriptionAndTooltip(moduleProblem.message);
      moduleProblem.fixes.forEach(fix -> info.registerFix(fix, List.of(), null, null, null));
      return info;
    }

    return null;
  }

  private static @NotNull PsiElement findPackagePrefix(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement candidate = ref;
    while (candidate instanceof PsiJavaCodeReferenceElement element) {
      if (element.resolve() instanceof PsiPackage) return candidate;
      candidate = element.getQualifier();
    }
    return ref;
  }
}
