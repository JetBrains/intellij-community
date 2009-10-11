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

/**
 * created at Nov 24, 2001
 * @author Jeka
 */
package com.intellij.refactoring.migration;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.NotNull;

class MigrationUsagesViewDescriptor implements UsageViewDescriptor {
  private final boolean isSearchInComments;
  private final MigrationMap myMigrationMap;

  public MigrationUsagesViewDescriptor(MigrationMap migrationMap, boolean isSearchInComments) {
    myMigrationMap = migrationMap;
    this.isSearchInComments = isSearchInComments;
  }

  public MigrationMap getMigrationMap() {
    return myMigrationMap;
  }

  @NotNull
  public PsiElement[] getElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  public String getProcessedElementsHeader() {
    return null;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.in.code.to.elements.from.migration.map", myMigrationMap.getName(),
                                     UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

  public String getInfo() {
    return RefactoringBundle.message("press.the.do.migrate.button", myMigrationMap.getName());
  }

}
