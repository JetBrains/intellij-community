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
package com.intellij.psi;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomRenameableTarget;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AliasingPsiTarget extends DelegatePsiTarget implements PomRenameableTarget<AliasingPsiTarget>{
  public AliasingPsiTarget(@NotNull PsiNamedElement element) {
    super(element);
  }

  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  public AliasingPsiTarget setName(@NotNull String newName) {
    return setAliasName(newName);
  }

   @NotNull
  public String getName() {
    return StringUtil.notNullize(getNameAlias(((PsiNamedElement)getNavigationElement()).getName()));
  }

  @NotNull
  public AliasingPsiTarget setAliasName(@NotNull String newAliasName) {
    return this;
  }

  @Nullable
  public String getNameAlias(@Nullable String delegatePsiTargetName) {
    return delegatePsiTargetName;
  }

  protected void renameTargets(@NotNull String newDelegateName) {
    final PsiNamedElement namedElement = (PsiNamedElement)getNavigationElement();
    if (!newDelegateName.equals(namedElement.getName())) {
      final RenameRefactoring refactoring =
        RefactoringFactory.getInstance(namedElement.getProject()).createRename(namedElement, newDelegateName);
      refactoring.run();

    }
  }
}