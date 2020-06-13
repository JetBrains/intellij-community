// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.targets;

import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import org.jetbrains.annotations.NotNull;

public final class AliasingPsiTargetUtil {
  public static void renameTargets(AliasingPsiTarget target, @NotNull String newDelegateName) {
    final PsiNamedElement namedElement = (PsiNamedElement)target.getNavigationElement();
    if (!newDelegateName.equals(namedElement.getName())) {
      final RenameRefactoring refactoring =
        RefactoringFactory.getInstance(namedElement.getProject()).createRename(namedElement, newDelegateName);
      refactoring.run();

    }
  }
}
