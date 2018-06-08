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
package com.intellij.psi.impl.migration;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class MigrationPackageImpl extends PsiPackageImpl implements PsiPackage {
  private final PsiMigrationImpl myMigration;

  public MigrationPackageImpl(PsiMigrationImpl migration, String qualifiedName) {
    super(migration.getManager(), qualifiedName);
    myMigration = migration;
  }

  public String toString() {
    return "MigrationPackage: " + getQualifiedName();
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myMigration.isValid();
  }

  @Override
  public void handleQualifiedNameChange(@NotNull String newQualifiedName) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VirtualFile[] occursInPackagePrefixes() {
    return VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public boolean mayHaveContentInScope(@NotNull GlobalSearchScope scope) {
    return true;
  }
}
