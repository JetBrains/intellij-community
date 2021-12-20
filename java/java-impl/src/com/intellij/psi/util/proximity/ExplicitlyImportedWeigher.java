// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util.proximity;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.NullableLazyKey;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
*/
public class ExplicitlyImportedWeigher extends ProximityWeigher {
  private static final NullableLazyKey<PsiPackage, ProximityLocation> PLACE_PACKAGE = NullableLazyKey.create("placePackage", location -> {
    PsiElement position = location.getPosition();
    return position == null ? null : getContextPackage(position);
  });
  private static final NotNullLazyKey<List<String>, ProximityLocation> PLACE_IMPORTED_NAMES =
    NotNullLazyKey.create("importedNames", location -> {
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

  @Nullable
  private static PsiPackage getContextPackage(PsiElement position) {
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
  public ImportWeight weigh(@NotNull final PsiElement element, @NotNull final ProximityLocation location) {
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

    if (element instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)element;
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
    CLASS_ON_DEMAND_TOP_LEVEL,
    CLASS_JAVA_LANG,
    MEMBER_SAME_PACKAGE,
    CLASS_IMPORTED,
    CLASS_DECLARED_IN_SAME_PACKAGE_TOP_LEVEL,
    DECLARED_IN_SAME_FILE,
    MEMBER_IMPORTED
  }
}
