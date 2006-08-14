/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 29, 2002
 * Time: 4:34:37 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

public class ImplementAbstractMethodAction extends BaseIntentionAction {
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.implement.abstract.method.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    final PsiMethod method = findMethod(file, offset);

    if (method == null || !method.isValid()) return false;
    setText(CodeInsightBundle.message("intention.implement.abstract.method.text", method.getName()));

    if (!method.getManager().isInProject(method)) return false;

    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    if (containingClass.isInterface() || method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      PsiSearchHelper helper = file.getManager().getSearchHelper();

      class MyElementProcessor implements PsiElementProcessor {
        private boolean myIsFound;

        public boolean isFound() {
          return myIsFound;
        }

        public boolean execute(PsiElement element) {
          if (element instanceof PsiClass) {
            PsiClass aClass = (PsiClass) element;
            if (aClass.findMethodBySignature(method, false) == null) {
              myIsFound = true;
              return false;
            }
          }
          return true;
        }

      }
      MyElementProcessor processor = new MyElementProcessor();
      helper.processInheritors(processor, containingClass, containingClass.getUseScope(), false);
      return processor.isFound();
    }

    return false;
  }

  private PsiMethod findMethod(PsiFile file, int offset) {
    PsiMethod method = _findMethod(file, offset);
    if (method == null) {
      method = _findMethod(file, offset - 1);
    }
    return method;
  }

  private PsiMethod _findMethod(PsiFile file, int offset) {
    return PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiMethod.class);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiMethod method = findMethod(file, editor.getCaretModel().getOffset());
    if (method == null) return;
    if (!editor.getContentComponent().isShowing()) return;
    new ImplementAbstractMethodHandler(project, editor, method).invoke();
  }

  public boolean startInWriteAction() {
    return false;
  }
}
