package com.intellij.codeInspection.actions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class UnimplementInterfaceAction implements IntentionAction {
  private String myName = "Interface";

  @NotNull
  public String getText() {
    return "Unimplement " + myName;
  }

  @NotNull
  public String getFamilyName() {
    return "Unimplement Interface/Class";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    final PsiReference psiReference = file.findReferenceAt(editor.getCaretModel().getOffset());
    if (psiReference == null) return false;

    final PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiReferenceList.class);
    if (referenceList == null) return false;

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceList, PsiClass.class);
    if (psiClass == null) return false;

    if (psiClass.getExtendsList() != referenceList && psiClass.getImplementsList() != referenceList) return false;

    final PsiElement target = psiReference.resolve();
    if (target == null || !(target instanceof PsiClass)) return false;

    PsiClass targetClass = (PsiClass)target;
    if (targetClass.isInterface()) {
      myName = "Interface";
    }
    else {
      myName = "Class";
    }
    
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiReference psiReference = file.findReferenceAt(editor.getCaretModel().getOffset());
    if (psiReference == null) return;

    final PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiReferenceList.class);
    if (referenceList == null) return;

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceList, PsiClass.class);
    if (psiClass == null) return;

    if (psiClass.getExtendsList() != referenceList && psiClass.getImplementsList() != referenceList) return;

    final PsiElement target = psiReference.resolve();
    if (target == null || !(target instanceof PsiClass)) return;

    PsiClass targetClass = (PsiClass)target;

    psiReference.getElement().delete();

    final PsiMethod[] psiMethods = targetClass.getAllMethods();
    for (PsiMethod psiMethod : psiMethods) {
      final PsiMethod[] implementingMethods = psiClass.findMethodsBySignature(psiMethod, false);
      for (PsiMethod implementingMethod : implementingMethods) {
        implementingMethod.delete();
      }
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
