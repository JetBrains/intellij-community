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
  public Integer weigh(@NotNull final PsiElement element, @NotNull final ProximityLocation location) {
    final PsiElement position = location.getPosition();
    if (position == null){
      return 0;
    }

    PsiUtilCore.ensureValid(position);

    final PsiFile elementFile = element.getContainingFile();
    final PsiFile positionFile = position.getContainingFile();
    if (positionFile != null && elementFile != null && positionFile.getOriginalFile().equals(elementFile.getOriginalFile())) {
      return 300;
    }

    if (element instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)element;
      final String qname = psiClass.getQualifiedName();
      if (qname != null) {
        boolean topLevel = psiClass.getContainingClass() == null;
        List<String> importedNames = PLACE_IMPORTED_NAMES.getValue(location);
        if (importedNames.contains(qname)) return 100;
        String packageName = StringUtil.getPackageName(qname);
        if ("java.lang".equals(packageName)) return 100;

        if (importedNames.contains(packageName)) {
          if (topLevel) {
            // The whole package is imported on demand
            return 80;
          }
          // Nested class which is eligible for on demand import (import static pkg.TopClass.*)
          // Rank higher than non-imported classes but still lower than top-level imported classes.
          return 60;
        }

        // check if anything from the same package is already imported in the file:
        //    people are likely to refer to the same subsystem as they're already working
        if (containsImport(importedNames, packageName)) return 50;
        final PsiPackage placePackage = PLACE_PACKAGE.getValue(location);
        if (placePackage != null) {
          Module elementModule = ModuleUtilCore.findModuleForPsiElement(element);
          if (location.getPositionModule() == elementModule && placePackage.equals(getContextPackage(element))) {
            return topLevel ? 200 : 50;
          }
        }
      }
      return 0;
    }
    if (element instanceof PsiMember) {
      String qname = PsiUtil.getMemberQualifiedName((PsiMember)element);
      if (qname != null && PLACE_IMPORTED_NAMES.getValue(location).contains(qname)) {
        return 400;
      }

      final PsiPackage placePackage = PLACE_PACKAGE.getValue(location);
      if (placePackage != null) {
        Module elementModule = ModuleUtilCore.findModuleForPsiElement(element);
        if (location.getPositionModule() == elementModule && placePackage.equals(getContextPackage(element))) {
          return 200;
        }
      }
    }
    return 0;
  }

  private static boolean containsImport(List<String> importedNames, final String pkg) {
    return ContainerUtil.or(importedNames, s -> s.startsWith(pkg + '.') || s.equals(pkg));
  }
}
