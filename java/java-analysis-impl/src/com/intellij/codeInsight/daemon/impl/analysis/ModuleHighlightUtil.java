// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.JavaModuleSystemEx;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.AddExportsDirectiveFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddUsesDirectiveFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.PsiPackageAccessibilityStatement.Role;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.util.JavaMultiReleaseUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNullElse;

// generates HighlightInfoType.ERROR-like HighlightInfos for modularity-related (Jigsaw) problems
final class ModuleHighlightUtil {

  static void checkUnusedServices(@NotNull PsiJavaModule module, @NotNull PsiFile file, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    Module host = ModuleUtilCore.findModuleForFile(file);
    if (host == null) {
      return;
    }
    List<PsiProvidesStatement> provides = JBIterable.from(module.getProvides()).toList();
    if (!provides.isEmpty()) {
      Set<String> exports = JBIterable.from(module.getExports()).map(PsiPackageAccessibilityStatement::getPackageName).filter(Objects::nonNull).toSet();
      Set<String> uses = JBIterable.from(module.getUses()).map(st -> qName(st.getClassReference())).filter(Objects::nonNull).toSet();
      for (PsiProvidesStatement statement : provides) {
        PsiJavaCodeReferenceElement ref = statement.getInterfaceReference();
        if (ref != null) {
          PsiElement target = ref.resolve();
          if (target instanceof PsiClass && ModuleUtilCore.findModuleForFile(target.getContainingFile()) == host) {
            String className = qName(ref);
            String packageName = StringUtil.getPackageName(className);
            if (!exports.contains(packageName) && !uses.contains(className)) {
              String message = JavaErrorBundle.message("module.service.unused");
              HighlightInfo.Builder info =
                HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(
                  requireNonNullElse(ref.getReferenceNameElement(), ref)).descriptionAndTooltip(message);
              ModCommandAction action1 = new AddExportsDirectiveFix(module, packageName, "");
              info.registerFix(action1, null, null, null, null);
              ModCommandAction action = new AddUsesDirectiveFix(module, className);
              info.registerFix(action, null, null, null, null);
              errorSink.accept(info);
            }
          }
        }
      }
    }
  }

  private static String qName(PsiJavaCodeReferenceElement ref) {
    return ref != null ? ref.getQualifiedName() : null;
  }

  static HighlightInfo.Builder checkModuleReference(@NotNull PsiImportModuleStatement statement) {
    PsiJavaModuleReferenceElement refElement = statement.getModuleReference();
    if (refElement == null) return null;
    PsiJavaModuleReference ref = refElement.getReference();
    assert ref != null : refElement.getParent();
    PsiJavaModule target = ref.resolve();
    if (target == null) return getUnresolvedJavaModuleReason(statement, refElement);
    for (JavaModuleSystem moduleSystem : JavaModuleSystem.EP_NAME.getExtensionList()) {
      if (!(moduleSystem instanceof JavaModuleSystemEx javaModuleSystemEx)) continue;
      JavaModuleSystemEx.ErrorWithFixes fixes = javaModuleSystemEx.checkAccess(target, statement);
      if (fixes == null) continue;
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(statement)
        .descriptionAndTooltip(fixes.message);
      fixes.fixes.forEach(fix -> info.registerFix(fix, null, null, null, null));
      return info;
    }
    return null;
  }

  static HighlightInfo.Builder checkModuleReference(@NotNull PsiRequiresStatement statement) {
    PsiJavaModuleReferenceElement refElement = statement.getReferenceElement();
    if (refElement != null) {
      PsiJavaModuleReference ref = refElement.getReference();
      assert ref != null : refElement.getParent();
      PsiJavaModule target = ref.resolve();
      if (target == null) {
        PsiJavaModuleReference ref1 = refElement.getReference();
        assert ref1 != null : refElement.getParent();

        ResolveResult[] results = ref1.multiResolve(true);
        if (results.length > 1) {
          // TODO: make as error or extract to inspection
          return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
            .range(refElement)
            .descriptionAndTooltip(JavaErrorBundle.message("module.ambiguous", refElement.getReferenceText()));
        }
      }
    }

    return null;
  }

  private static @NotNull HighlightInfo.Builder getUnresolvedJavaModuleReason(@NotNull PsiElement parent, @NotNull PsiJavaModuleReferenceElement refElement) {
    PsiJavaModuleReference ref = refElement.getReference();
    assert ref != null : refElement.getParent();

    ResolveResult[] results = ref.multiResolve(true);
    switch (results.length) {
      case 0:
        if (IncompleteModelUtil.isIncompleteModel(parent)) {
          return HighlightUtil.getPendingReferenceHighlightInfo(refElement);
        } else {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
            .range(refElement)
            .descriptionAndTooltip(JavaErrorBundle.message("module.not.found", refElement.getReferenceText()));
        }
      case 1:
        String message = JavaErrorBundle.message("module.not.on.path", refElement.getReferenceText());
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
          .range(refElement)
          .descriptionAndTooltip(message);
        List<IntentionAction> registrar = new ArrayList<>();
        QuickFixFactory.getInstance().registerOrderEntryFixes(ref, registrar);
        QuickFixAction.registerQuickFixActions(info, null, registrar);
        return info;
      default:
        return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
          .range(refElement)
          .descriptionAndTooltip(JavaErrorBundle.message("module.ambiguous", refElement.getReferenceText()));
    }
  }

  static HighlightInfo.Builder checkPackageReference(@NotNull PsiPackageAccessibilityStatement statement, @NotNull PsiFile file) {
    PsiJavaCodeReferenceElement refElement = statement.getPackageReference();
    if (refElement != null) {
      Module module = ModuleUtilCore.findModuleForFile(file);
      if (module != null) {
        PsiElement target = refElement.resolve();
        PsiDirectory[] directories = PsiDirectory.EMPTY_ARRAY;
        if (target instanceof PsiPackage psiPackage) {
          boolean inTests = ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(file.getVirtualFile());
          directories = psiPackage.getDirectories(module.getModuleScope(inTests));
          Module mainMultiReleaseModule = JavaMultiReleaseUtil.getMainMultiReleaseModule(module);
          if (mainMultiReleaseModule != null) {
            directories = ArrayUtil.mergeArrays(directories, psiPackage.getDirectories(mainMultiReleaseModule.getModuleScope(inTests)));
          }
        }
        String packageName = statement.getPackageName();
        boolean opens = statement.getRole() == Role.OPENS;
        HighlightInfoType type = opens ? HighlightInfoType.WARNING : HighlightInfoType.ERROR;
        if (directories.length == 0) {
          String message = JavaErrorBundle.message("package.not.found", packageName);
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(type).range(refElement).descriptionAndTooltip(message);
          IntentionAction action = QuickFixFactory.getInstance().createCreateClassInPackageInModuleFix(module, packageName);
          if (action != null) {
            info.registerFix(action, null, null, null, null);
          }
          return info;
        }
        if (packageName != null && isPackageEmpty(directories, packageName, opens)) {
          String message = JavaErrorBundle.message("package.is.empty", packageName);
          HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(type).range(refElement).descriptionAndTooltip(message);
          IntentionAction action = QuickFixFactory.getInstance().createCreateClassInPackageInModuleFix(module, packageName);
          if (action != null) {
            info.registerFix(action, null, null, null, null);
          }
          return info;
        }
      }
    }

    return null;
  }

  private static boolean isPackageEmpty(PsiDirectory @NotNull [] directories, @NotNull String packageName, boolean anyFile) {
    if (anyFile) {
      return !ContainerUtil.exists(directories, dir -> dir.getFiles().length > 0);
    }
    else {
      return PsiUtil.isPackageEmpty(directories, packageName);
    }
  }
}