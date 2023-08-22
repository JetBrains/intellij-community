// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.migration;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMigration;
import com.intellij.psi.impl.PsiManagerImpl;
import org.jetbrains.annotations.NotNull;

@Service
public final class PsiMigrationManager {
  private static final Logger LOG = Logger.getInstance(PsiMigrationManager.class);

  public static PsiMigrationManager getInstance(Project project) {
    return project.getService(PsiMigrationManager.class);
  }

  private final Project myProject;
  private PsiMigrationImpl myCurrentMigration;

  public PsiMigrationManager(Project project) {
    myProject = project;
  }

  public PsiMigrationImpl getCurrentMigration() {
    return myCurrentMigration;
  }

  /**
   * Initiates a migrate refactoring. The refactoring is finished when
   * {@link PsiMigration#finish()} is called.
   *
   * @return the migrate operation object.
   */
  @NotNull
  public PsiMigration startMigration() {
    LOG.assertTrue(myCurrentMigration == null);
    myCurrentMigration = new PsiMigrationImpl(this, JavaPsiFacade.getInstance(myProject),
                                              (PsiManagerImpl)PsiManager.getInstance(myProject));
    return myCurrentMigration;
  }

  public void migrationModified(boolean terminated) {
    if (terminated) {
      myCurrentMigration = null;
    }

    PsiManager.getInstance(myProject).dropPsiCaches();
  }
}
