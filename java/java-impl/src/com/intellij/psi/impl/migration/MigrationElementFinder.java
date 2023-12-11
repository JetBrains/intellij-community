// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.migration;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public final class MigrationElementFinder extends PsiElementFinder implements DumbAware {
  private final Project myProject;

  public MigrationElementFinder(Project project) {
    myProject = project;
  }

  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      return migration.getMigrationClass(qualifiedName);
    }
    return null;
  }

  @Override
  public PsiClass @NotNull [] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      final PsiClass migrationClass = migration.getMigrationClass(qualifiedName);
      if (migrationClass != null) {
        return new PsiClass[]{migrationClass};
      }
    }
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public PsiClass @NotNull [] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      List<PsiClass> classes = migration.getMigrationClasses(psiPackage.getQualifiedName());
      return classes.toArray(PsiClass.EMPTY_ARRAY);
    }
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public PsiPackage @NotNull [] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      List<PsiPackage> packages = migration.getMigrationPackages(psiPackage.getQualifiedName());
      return packages.toArray(PsiPackage.EMPTY_ARRAY);
    }
    return PsiPackage.EMPTY_ARRAY;
  }

  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      return migration.getMigrationPackage(qualifiedName);
    }
    return null;
  }
}
