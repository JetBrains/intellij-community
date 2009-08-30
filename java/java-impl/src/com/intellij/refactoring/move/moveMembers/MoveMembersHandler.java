package com.intellij.refactoring.move.moveMembers;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.Nullable;

public class MoveMembersHandler extends MoveHandlerDelegate {
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    for(PsiElement element: elements) {
      if (!isFieldOrStaticMethod(element)) return false;
    }
    return super.canMove(elements, targetContainer);
  }

  public boolean isValidTarget(final PsiElement psiElement) {
    return psiElement instanceof PsiClass && !(psiElement instanceof PsiAnonymousClass);
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    MoveMembersImpl.doMove(project, elements, targetContainer, callback);
  }

  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (isFieldOrStaticMethod(element)) {
      MoveMembersImpl.doMove(project, new PsiElement[]{element}, null, null);
      return true;
    }
    return false;
  }

  private static boolean isFieldOrStaticMethod(final PsiElement element) {
    if (element instanceof PsiField) return true;
    if (element instanceof PsiMethod) {
      if (element instanceof JspHolderMethod) return false;
      return ((PsiMethod) element).hasModifierProperty(PsiModifier.STATIC);
    }
    return false;
  }
}
