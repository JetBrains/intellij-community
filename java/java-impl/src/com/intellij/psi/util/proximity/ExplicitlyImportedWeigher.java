// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util.proximity;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.NullableLazyKey;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

public final class ExplicitlyImportedWeigher extends ProximityWeigher {
  private static final NullableLazyKey<PsiPackage, ProximityLocation> PLACE_PACKAGE = NullableLazyKey.create("placePackage", location -> {
    PsiElement position = location.getPosition();
    return position == null ? null : getContextPackage(position);
  });
  private static final NullableLazyKey<List<PsiJavaModule>, ProximityLocation> PLACE_IMPORTED_MODULES =
    NullableLazyKey.create("importedModuleNames", location -> {
      final PsiJavaFile psiJavaFile = PsiTreeUtil.getContextOfType(location.getPosition(), PsiJavaFile.class, false);
      final PsiImportList importList = psiJavaFile == null ? null : psiJavaFile.getImportList();
      if (importList == null) return Collections.emptyList();

      BiConsumer<List<PsiJavaModule>, PsiJavaModule> append = (list, module) -> {
        if (module != null) {
          list.add(module);
          list.addAll(JavaModuleGraphUtil.getAllTransitiveDependencies(module));
        }
      };

      List<PsiJavaModule> importedModules = new ArrayList<>();
      for (PsiImportModuleStatement statement : importList.getImportModuleStatements()) {
        append.accept(importedModules, statement.resolveTargetModule());
      }
      for (PsiImportStatementBase statement : ImportsUtil.getAllImplicitImports(psiJavaFile)) {
        if (statement instanceof PsiImportModuleStatement moduleStatement) {
          append.accept(importedModules, moduleStatement.resolveTargetModule());
        }
      }
      return importedModules;
    });
  private static final NotNullLazyKey<List<String>, ProximityLocation> PLACE_IMPORTED_NAMES =
    NotNullLazyKey.createLazyKey("importedNames", location -> {
      final PsiJavaFile psiJavaFile = PsiTreeUtil.getContextOfType(location.getPosition(), PsiJavaFile.class, false);
      final PsiImportList importList = psiJavaFile == null ? null : psiJavaFile.getImportList();
      if (importList == null) return Collections.emptyList();

      List<String> importedNames = new ArrayList<>();
      for (PsiImportStatementBase statement : importList.getAllImportStatements()) {
        PsiJavaCodeReferenceElement reference = statement.getImportReference();
        ContainerUtil.addIfNotNull(importedNames, reference == null ? null : reference.getQualifiedName());
      }

      return importedNames;
    });

  private static @Nullable PsiPackage getContextPackage(PsiElement position) {
    PsiFile file = position.getContainingFile();
    if (file == null) return null;

    PsiFile originalFile = file.getOriginalFile();
    while (true) {
      PsiElement context = originalFile.getContext();
      if (context == null) {
        PsiDirectory parent = originalFile.getParent();
        if (parent != null) {
          return JavaDirectoryService.getInstance().getPackage(parent);
        }
        return null;
      }

      PsiFile containingFile = context.getContainingFile();
      if (containingFile == null) return null;

      originalFile = containingFile.getOriginalFile();
    }
  }

  @Override
  public ImportWeight weigh(final @NotNull PsiElement element, final @NotNull ProximityLocation location) {
    final PsiElement position = location.getPosition();
    if (position == null){
      return ImportWeight.UNKNOWN;
    }

    PsiUtilCore.ensureValid(position);

    final PsiFile elementFile = element.getContainingFile();
    final PsiFile positionFile = position.getContainingFile();
    if (positionFile != null && elementFile != null && positionFile.getOriginalFile().equals(elementFile.getOriginalFile())) {
      return ImportWeight.DECLARED_IN_SAME_FILE;
    }

    if (element instanceof PsiClass psiClass) {
      final String qname = psiClass.getQualifiedName();
      if (qname != null) {
        boolean topLevel = psiClass.getContainingClass() == null;
        List<String> importedNames = PLACE_IMPORTED_NAMES.getValue(location);
        if (importedNames.contains(qname)) return ImportWeight.CLASS_IMPORTED;
        String packageName = StringUtil.getPackageName(qname);
        if ("java.lang".equals(packageName)) return ImportWeight.CLASS_JAVA_LANG;

        if (importedNames.contains(packageName)) {
          if (topLevel) {
            // The whole package is imported on demand
            return ImportWeight.CLASS_ON_DEMAND_TOP_LEVEL;
          }
          // Nested class which is eligible for on demand import (import static pkg.TopClass.*)
          // Rank higher than non-imported classes but still lower than top-level imported classes.
          return ImportWeight.CLASS_ON_DEMAND_NESTED;
        }

        List<PsiJavaModule> importedModules = PLACE_IMPORTED_MODULES.getValue(location);
        if (importedModules != null && !importedModules.isEmpty()) {
          PsiJavaModule suggestedModule = JavaModuleGraphHelper.getInstance().findDescriptorByElement(element);
          if (suggestedModule != null && importedModules.contains(suggestedModule)) return ImportWeight.MODULE_IMPORTED;
        }

        final PsiPackage placePackage = PLACE_PACKAGE.getValue(location);
        if (placePackage != null) {
          Module elementModule = ModuleUtilCore.findModuleForPsiElement(element);
          if (location.getPositionModule() == elementModule && placePackage.equals(getContextPackage(element))) {
            return topLevel ? ImportWeight.CLASS_DECLARED_IN_SAME_PACKAGE_TOP_LEVEL : ImportWeight.CLASS_DECLARED_IN_SAME_PACKAGE_NESTED;
          }
        }
        // check if anything from the same package is already imported in the file:
        //    people are likely to refer to the same subsystem as they're already working
        if (containsImport(importedNames, packageName)) return ImportWeight.CLASS_HAS_SAME_PACKAGE_IMPORT;
      }
      return ImportWeight.UNKNOWN;
    }
    if (element instanceof PsiMember) {
      String qname = PsiUtil.getMemberQualifiedName((PsiMember)element);
      if (qname != null && PLACE_IMPORTED_NAMES.getValue(location).contains(qname)) {
        return ImportWeight.MEMBER_IMPORTED;
      }

      final PsiPackage placePackage = PLACE_PACKAGE.getValue(location);
      if (placePackage != null) {
        Module elementModule = ModuleUtilCore.findModuleForPsiElement(element);
        if (location.getPositionModule() == elementModule && placePackage.equals(getContextPackage(element))) {
          return ImportWeight.MEMBER_SAME_PACKAGE;
        }
      }
    }
    return ImportWeight.UNKNOWN;
  }

  private static boolean containsImport(List<String> importedNames, final String pkg) {
    return ContainerUtil.or(importedNames, s -> s.startsWith(pkg + '.') || s.equals(pkg));
  }

  enum ImportWeight {
    UNKNOWN,
    CLASS_HAS_SAME_PACKAGE_IMPORT,
    CLASS_DECLARED_IN_SAME_PACKAGE_NESTED,
    CLASS_ON_DEMAND_NESTED,
    MODULE_IMPORTED,
    CLASS_ON_DEMAND_TOP_LEVEL,
    CLASS_JAVA_LANG,
    MEMBER_SAME_PACKAGE,
    CLASS_IMPORTED,
    CLASS_DECLARED_IN_SAME_PACKAGE_TOP_LEVEL,
    DECLARED_IN_SAME_FILE,
    MEMBER_IMPORTED
  }
}
