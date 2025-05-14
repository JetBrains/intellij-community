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
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.util.ImportsUtil.getAllImplicitImports;

class ImportsAreUsedVisitor extends JavaRecursiveElementWalkingVisitor {

  private final PsiJavaFile myFile;
  private final List<PsiImportStatementBase> importStatements;
  private final List<PsiImportStatementBase> usedImportStatements = new ArrayList<>();
  private final List<PsiImportStatementBase> implicitlyUsedImportStatements = new ArrayList<>();
  private final Set<PsiImportStatementBase> highLevelModuleImports = new HashSet<>();
  private final JavaCodeStyleSettings settings;

  ImportsAreUsedVisitor(@NotNull PsiJavaFile file) {
    myFile = file;
    settings = JavaCodeStyleSettings.getInstance(file);
    final PsiImportList importList = file.getImportList();
    if (importList == null) {
      importStatements = Collections.emptyList();
    }
    else {
      final PsiImportStatementBase[] importStatements = importList.getAllImportStatements();
      this.importStatements = new ArrayList<>(Arrays.asList(importStatements));
      this.implicitlyUsedImportStatements.addAll(getAllImplicitImports(file));
      this.importStatements.sort(ImportStatementComparator.getInstance());

      highLevelModuleImports.addAll(ImportUtils.optimizeModuleImports(myFile));
      List<PsiImportStatementBase> unusedModuleImports =
        ContainerUtil.filter(this.importStatements,
                             t -> t instanceof PsiImportModuleStatement importModuleStatement &&
                                  !highLevelModuleImports.contains(importModuleStatement));

      this.importStatements.removeAll(unusedModuleImports);
      this.importStatements.addAll(unusedModuleImports);
    }
  }

  @Override
  public void visitImportList(@NotNull PsiImportList list) {
    //ignore imports
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
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
    PsiElement element = resolveResult.getElement();
    if (element == null) {
      JavaResolveResult[] results = reference.multiResolve(false);
      if (results.length > 0) {
        element = results[0].getElement();
      }
    }
    if (!(element instanceof PsiMember member)) {
      return;
    }
    if (findImport(member, usedImportStatements) != null) {
      return;
    }
    if (findImport(member, implicitlyUsedImportStatements) != null) {
      return;
    }
    final PsiImportStatementBase foundImport = findImport(member, importStatements);
    if (foundImport != null) {
      importStatements.remove(foundImport);
      usedImportStatements.add(foundImport);
    }
  }

  private @Nullable PsiImportStatementBase findImport(@NotNull PsiMember member, List<? extends PsiImportStatementBase> importStatements) {
    final String memberQualifiedName;
    final String memberPackageName;
    final PsiClass containingClass = member.getContainingClass();
    if (member instanceof PsiClass referencedClass) {
      memberQualifiedName = referencedClass.getQualifiedName();
      memberPackageName = memberQualifiedName != null ? StringUtil.getPackageName(memberQualifiedName) : null;
    }
    else {
      if (!member.hasModifierProperty(PsiModifier.STATIC) || containingClass == null) {
        return null;
      }
      memberPackageName = containingClass.getQualifiedName();
      memberQualifiedName = memberPackageName + '.' + member.getName();
    }
    if (memberPackageName == null) {
      return null;
    }
    ImportUtils.OnDemandImportConflict conflicts = ImportUtils.findOnDemandImportConflict(memberQualifiedName, myFile);
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
        if (importStatement instanceof PsiImportModuleStatement && conflicts.hasConflictForModules()) {
          continue;
        }
        if (!(importStatement instanceof PsiImportModuleStatement) &&
            importStatement.isOnDemand() &&
            conflicts.hasConflictForOnDemand()) {
          continue;
        }
        if (importStatement instanceof PsiImportModuleStatement psiImportModuleStatement &&
            !member.hasModifierProperty(PsiModifier.STATIC)) {
          if (psiImportModuleStatement.findImportedPackage(memberPackageName) != null) {
            return importStatement;
          }
        }
        final PsiElement target = importStatement.resolve();
        if (target instanceof PsiPackage aPackage) {
          if (memberPackageName.equals(aPackage.getQualifiedName())) {
            return importStatement;
          }
        }
        else if (target instanceof PsiClass aClass) {
          // a regular import statement does NOT import inner classes from super classes, but a static import does
          if (importStatement instanceof PsiImportStaticStatement) {
            if (member.hasModifierProperty(PsiModifier.STATIC)) {
              PsiManager manager = aClass.getManager();
              if (manager.areElementsEquivalent(aClass, containingClass) ||
                  (containingClass != null &&
                   //impossible to reference to static methods in interfaces via inheritances
                   !(member instanceof PsiMethod && containingClass.isInterface()) &&
                   aClass.isInheritor(containingClass, true))) {
                return importStatement;
              }
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

  PsiImportStatementBase @NotNull [] getUnusedImportStatements() {
    if (importStatements.isEmpty()) {
      return PsiImportStatementBase.EMPTY_ARRAY;
    }
    if (!settings.isDeleteUnusedModuleImports()) {
      importStatements.removeAll(highLevelModuleImports);
    }

    return importStatements.toArray(PsiImportStatementBase.EMPTY_ARRAY);
  }
}