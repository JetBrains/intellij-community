/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

      List<String> importedNames = ContainerUtil.newArrayList();
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
      final String qname = ((PsiClass) element).getQualifiedName();
      if (qname != null) {
        List<String> importedNames = PLACE_IMPORTED_NAMES.getValue(location);
        if (importedNames.contains(qname) || "java.lang".equals(StringUtil.getPackageName(qname))) {
          return 100;
        }

        // check if anything from the same package is already imported in the file:
        //    people are likely to refer to the same subsystem as they're already working
        if (containsImport(importedNames, StringUtil.getPackageName(qname))) {
          return 50;
        }
      }

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
