/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
 * created at Nov 12, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveInner;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.Nullable;

public class MoveInnerImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveInner.MoveInnerImpl");

  public static final String REFACTORING_NAME = RefactoringBundle.message("move.inner.to.upper.level.title");

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
            PackageChooserDialog chooser = new PackageChooserDialog("Select Target Package", innerClass.getProject());
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
}
