package com.intellij.codeInspection.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
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
    if (!(file instanceof PsiJavaFile)) return false;
    final PsiReference psiReference = TargetElementUtil.findReference(editor);
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

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    new WriteCommandAction(project, "Unimplement Interface", file) {
      protected void run(Result result) throws Throwable {
        final PsiReference psiReference = file.findReferenceAt(editor.getCaretModel().getOffset());
        if (psiReference == null) return;

        final PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiReferenceList.class);
        if (referenceList == null) return;

        final PsiClass psiClass = PsiTreeUtil.getParentOfType(referenceList, PsiClass.class);
        if (psiClass == null) return;

        if (psiClass.getExtendsList() != referenceList && psiClass.getImplementsList() != referenceList) return;

        final PsiElement target = psiReference.resolve();
        if (target == null || !(target instanceof PsiClass)) return;

        if (ReadonlyStatusHandler.getInstance(project)
          .ensureFilesWritable(file.getVirtualFile()).hasReadonlyFiles()) return;

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
    }.execute();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
