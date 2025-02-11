// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.JavaModuleSystemEx;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.AddExportsDirectiveFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddUsesDirectiveFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.java.codeserver.core.JavaServiceProviderUtil;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.PsiPackageAccessibilityStatement.Role;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

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
                HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(range(ref)).descriptionAndTooltip(message);
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
          Module mainMultiReleaseModule = MultiReleaseUtil.getMainMultiReleaseModule(module);
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

  static void checkServiceImplementations(@NotNull PsiProvidesStatement statement, @NotNull PsiFile file,
                                          @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiReferenceList implRefList = statement.getImplementationList();
    if (implRefList == null) return;

    PsiJavaCodeReferenceElement intRef = statement.getInterfaceReference();
    PsiElement intTarget = intRef != null ? intRef.resolve() : null;

    Set<String> filter = new HashSet<>();
    for (PsiJavaCodeReferenceElement implRef : implRefList.getReferenceElements()) {
      String refText = implRef.getQualifiedName();
      if (!filter.add(refText)) {
        String message = JavaErrorBundle.message("module.duplicate.impl", refText);
        HighlightInfo.Builder info = createDuplicateReference(implRef, message);
        errorSink.accept(info);
        continue;
      }

      if (!(intTarget instanceof PsiClass psiClass)) continue;

      PsiElement implTarget = implRef.resolve();
      if (implTarget instanceof PsiClass implClass) {
        Module fileModule = ModuleUtilCore.findModuleForFile(file);
        Module implModule = ModuleUtilCore.findModuleForFile(implClass.getContainingFile());
        if (fileModule != implModule && !MultiReleaseUtil.areMainAndAdditionalMultiReleaseModules(implModule, fileModule)) {
          String message = JavaErrorBundle.message("module.service.alien");
          HighlightInfo.Builder info =
            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message);
          errorSink.accept(info);
        }

        PsiMethod provider = JavaServiceProviderUtil.findServiceProviderMethod(implClass);
        if (provider != null) {
          PsiType type = provider.getReturnType();
          PsiClass typeClass = type instanceof PsiClassType classType ? classType.resolve() : null;
          if (!InheritanceUtil.isInheritorOrSelf(typeClass, psiClass, true)) {
            String message = JavaErrorBundle.message("module.service.provider.type", implClass.getName());
            HighlightInfo.Builder info =
              HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message);
            errorSink.accept(info);
          }
        }
        else if (InheritanceUtil.isInheritorOrSelf(implClass, psiClass, true)) {
          if (implClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            String message = JavaErrorBundle.message("module.service.abstract", implClass.getName());
            HighlightInfo.Builder info =
              HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message);
            errorSink.accept(info);
          }
          else if (!(ClassUtil.isTopLevelClass(implClass) || implClass.hasModifierProperty(PsiModifier.STATIC))) {
            String message = JavaErrorBundle.message("module.service.inner", implClass.getName());
            HighlightInfo.Builder info =
              HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message);
            errorSink.accept(info);
          }
          else if (!PsiUtil.hasDefaultConstructor(implClass)) {
            String message = JavaErrorBundle.message("module.service.no.ctor", implClass.getName());
            HighlightInfo.Builder info =
              HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message);
            errorSink.accept(info);
          }
        }
        else {
          String message = JavaErrorBundle.message("module.service.impl");
          HighlightInfo.Builder info =
            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range(implRef)).descriptionAndTooltip(message);
          PsiClassType type = JavaPsiFacade.getElementFactory(file.getProject()).createType(psiClass);
          IntentionAction action = QuickFixFactory.getInstance().createExtendsListFix(implClass, type, true);
          info.registerFix(action, null, null, null, null);
          errorSink.accept(info);
        }
      }
    }
  }

  private static @NotNull PsiElement range(@NotNull PsiJavaCodeReferenceElement refElement) {
    return ObjectUtils.notNull(refElement.getReferenceNameElement(), refElement);
  }

  private static @NotNull HighlightInfo.Builder createDuplicateReference(@NotNull PsiElement refElement, @NotNull @NlsContexts.DetailedDescription String message) {
    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message);
    IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(refElement, QuickFixBundle.message("delete.reference.fix.text"));
    info.registerFix(action, null, null, null, null);
    return info;
  }
}