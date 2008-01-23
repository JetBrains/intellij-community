package com.intellij.refactoring.move;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class MoveHandlerDelegate {
  public static final ExtensionPointName<MoveHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.moveHandler");

  public boolean canMove(PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    return false;
  }

  public void doMove(final Project project, final PsiElement[] elements,
                     @Nullable final PsiElement targetContainer, @Nullable final MoveCallback callback) {
  }

  @Nullable
  public PsiElement[] adjustForMove(Project project, PsiElement[] sourceElements, PsiElement targetElement) {
    return sourceElements;
  }

  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext,
                           @Nullable final PsiReference reference) {
    return false;
  }
}
