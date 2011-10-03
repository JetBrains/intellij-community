/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.migration;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class MigrationElementFinder extends PsiElementFinder implements DumbAware {
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

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      final PsiClass migrationClass = migration.getMigrationClass(qualifiedName);
      if (migrationClass != null) {
        return new PsiClass[]{migrationClass};
      }
    }
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      List<PsiClass> classes = migration.getMigrationClasses(psiPackage.getQualifiedName());
      return classes.toArray(new PsiClass[classes.size()]);
    }
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    PsiMigrationImpl migration = PsiMigrationManager.getInstance(myProject).getCurrentMigration();
    if (migration != null) {
      List<PsiPackage> packages = migration.getMigrationPackages(psiPackage.getQualifiedName());
      return packages.toArray(new PsiPackage[packages.size()]);
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
