// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.move.moveInner;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.Nullable;

public final class MoveInnerImpl {
  private static final Logger LOG = Logger.getInstance(MoveInnerImpl.class);

  public static void doMove(final Project project, PsiElement[] elements, final MoveCallback moveCallback, @Nullable PsiElement targetContainer) {
    if (elements.length != 1) return;
    final PsiClass aClass = (PsiClass) elements[0];
    boolean condition = aClass.getContainingClass() != null;
    LOG.assertTrue(condition);

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;
    if (targetContainer == null) {
      targetContainer = getTargetContainer(aClass, true);
    }
    if (targetContainer == null) return;

    final MoveInnerDialog dialog = new MoveInnerDialog(
            project,
            aClass,
            new MoveInnerProcessor(project, moveCallback),
            targetContainer);
    dialog.show();

  }

  /**
   * must be called in atomic action
   */
  @Nullable
  public static PsiElement getTargetContainer(PsiClass innerClass, final boolean chooseIfNotUnderSource) {
    final PsiClass outerClass = innerClass.getContainingClass();
    assert outerClass != null; // Only inner classes allowed.

    PsiElement outerClassParent = outerClass.getParent();
    while (outerClassParent != null) {
      if (outerClassParent instanceof PsiClass && !(outerClassParent instanceof PsiAnonymousClass)) {
        return outerClassParent;
      }
      else if (outerClassParent instanceof PsiFile) {
        final PsiDirectory directory = innerClass.getContainingFile().getContainingDirectory();
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if (aPackage == null) {
          if (chooseIfNotUnderSource) {
            PackageChooserDialog chooser = new PackageChooserDialog(JavaBundle.message("move.inner.select.target.package.title"), innerClass.getProject());
            if (!chooser.showAndGet()) {
              return null;
            }
            final PsiPackage chosenPackage = chooser.getSelectedPackage();
            if (chosenPackage == null) return null;
            return chosenPackage.getDirectories()[0];
          }

          return null;
        }
        return directory;
      }
      outerClassParent = outerClassParent.getParent();
    }
    // should not happen
    LOG.assertTrue(false);
    return null;
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("move.inner.to.upper.level.title");
  }
}
