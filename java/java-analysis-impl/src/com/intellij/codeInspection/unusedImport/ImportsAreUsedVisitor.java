/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.intellij.codeInspection.unusedImport;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class ImportsAreUsedVisitor extends JavaRecursiveElementWalkingVisitor {

  private final PsiJavaFile myFile;
  private final List<PsiImportStatementBase> importStatements;
  private final List<PsiImportStatementBase> usedImportStatements = new ArrayList<>();

  ImportsAreUsedVisitor(PsiJavaFile file) {
    myFile = file;
    final PsiImportList importList = file.getImportList();
    if (importList == null) {
      importStatements = Collections.emptyList();
    } else {
      final PsiImportStatementBase[] importStatements = importList.getAllImportStatements();
      this.importStatements = new ArrayList<>(Arrays.asList(importStatements));
      Collections.sort(this.importStatements, ImportStatementComparator.getInstance());
    }
  }

  @Override
  public void visitElement(PsiElement element) {
    if (importStatements.isEmpty()) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
    followReferenceToImport(reference);
    super.visitReferenceElement(reference);
  }

  private void followReferenceToImport(PsiJavaCodeReferenceElement reference) {
    if (reference.getQualifier() != null) {
      // it's already fully qualified, so the import statement wasn't
      // responsible
      return;
    }
    // during typing there can be incomplete code
    final JavaResolveResult resolveResult = reference.advancedResolve(true);
    final PsiElement element = resolveResult.getElement();
    if (!(element instanceof PsiMember)) {
      return;
    }
    final PsiMember member = (PsiMember)element;
    if (findImport(member, usedImportStatements) != null) {
      return;
    }
    final PsiImportStatementBase foundImport = findImport(member, importStatements);
    if (foundImport != null) {
      importStatements.remove(foundImport);
      usedImportStatements.add(foundImport);
    }
  }

  private PsiImportStatementBase findImport(PsiMember member, List<PsiImportStatementBase> importStatements) {
    final String qualifiedName;
    final String packageName;
    final PsiClass containingClass = member.getContainingClass();
    if (member instanceof PsiClass) {
      final PsiClass referencedClass = (PsiClass)member;
      qualifiedName = referencedClass.getQualifiedName();
      packageName = qualifiedName != null ? StringUtil.getPackageName(qualifiedName) : null;
    }
    else {
      if (!member.hasModifierProperty(PsiModifier.STATIC) || containingClass == null) {
        return null;
      }
      packageName = containingClass.getQualifiedName();
      qualifiedName = packageName + '.' + member.getName();
    }
    if (packageName == null) {
      return null;
    }
    final boolean hasOnDemandImportConflict = ImportUtils.hasOnDemandImportConflict(qualifiedName, myFile);
    for (PsiImportStatementBase importStatement : importStatements) {
      if (!importStatement.isOnDemand()) {
        final PsiJavaCodeReferenceElement reference = importStatement.getImportReference();
        if (reference == null) {
          continue;
        }
        final JavaResolveResult[] targets = reference.multiResolve(false);
        for (JavaResolveResult target : targets) {
          if (member.equals(target.getElement())) {
            return importStatement;
          }
        }
      }
      else {
        if (hasOnDemandImportConflict) {
          continue;
        }
        final PsiElement target = importStatement.resolve();
        if (target instanceof PsiPackage) {
          final PsiPackage aPackage = (PsiPackage)target;
          if (packageName.equals(aPackage.getQualifiedName())) {
            return importStatement;
          }
        }
        else if (target instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)target;
          // a regular import statement does NOT import inner classes from super classes, but a static import does
          if (importStatement instanceof PsiImportStaticStatement) {
            if (member.hasModifierProperty(PsiModifier.STATIC) && InheritanceUtil.isInheritorOrSelf(aClass, containingClass, true)) {
              return importStatement;
            }
          }
          else if (importStatement instanceof PsiImportStatement && member instanceof PsiClass && aClass.equals(containingClass)) {
            return importStatement;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  PsiImportStatementBase[] getUnusedImportStatements() {
    if (importStatements.isEmpty()) {
      return PsiImportStatementBase.EMPTY_ARRAY;
    }
    return importStatements.toArray(new PsiImportStatementBase[importStatements.size()]);
  }
}