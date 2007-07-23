/*
 * User: anna
 * Date: 08-Jul-2007
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeannotateIntentionAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#" + DeannotateIntentionAction.class.getName());

  @NotNull
  public String getText() {
    return CodeInsightBundle.message("deannotate.intention.action.text");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiModifierListOwner listOwner = getContainer(editor, file);
    return listOwner != null && ExternalAnnotationsManager.getInstance(project).findExternalAnnotations(listOwner) != null;
  }

  @Nullable
  public static PsiModifierListOwner getContainer(final Editor editor, final PsiFile file) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false);
    if (listOwner == null) {
      final PsiIdentifier psiIdentifier = PsiTreeUtil.getParentOfType(element, PsiIdentifier.class, false);
      if (psiIdentifier != null && psiIdentifier.getParent() instanceof PsiModifierListOwner) {
        listOwner = (PsiModifierListOwner)psiIdentifier.getParent();
      } else {
        PsiExpression expression = PsiTreeUtil.getParentOfType(element, PsiExpression.class);
        if (expression != null) {
          while (expression.getParent() instanceof PsiExpression) { //get top level expression
            expression = (PsiExpression)expression.getParent();
            if (expression instanceof PsiAssignmentExpression) break;
          }
          if (expression instanceof PsiMethodCallExpression) {
            final PsiMethod psiMethod = ((PsiMethodCallExpression)expression).resolveMethod();
            if (psiMethod != null) {
              return psiMethod;
            }
          }
          final PsiElement parent = expression.getParent();
          if (parent instanceof PsiExpressionList) {  //try to find corresponding formal parameter
            int idx = -1;
            final PsiExpression[] args = ((PsiExpressionList)parent).getExpressions();
            for (int i = 0; i < args.length; i++) {
              PsiExpression arg = args[i];
              if (PsiTreeUtil.isAncestor(arg, expression, false)) {
                idx = i;
                break;
              }
            }

            if (idx > -1) {
              PsiElement grParent = parent.getParent();
              if (grParent instanceof PsiCall) {
                PsiMethod method = ((PsiCall)grParent).resolveMethod();
                if (method != null) {
                  final PsiParameter[] parameters = method.getParameterList().getParameters();
                  if (parameters.length > idx) {
                    return parameters[idx];
                  }
                }
              }
            }
          }
        }
      }
    }
    return listOwner;
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiModifierListOwner listOwner = getContainer(editor, file);
    final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    final PsiAnnotation[] externalAnnotations = annotationsManager.findExternalAnnotations(listOwner);
    JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<PsiAnnotation>(CodeInsightBundle.message("deannotate.intention.chooser.title"), externalAnnotations) {
      public PopupStep onChosen(final PsiAnnotation selectedValue, final boolean finalChoice) {
        new WriteCommandAction(project){
          protected void run(final Result result) throws Throwable {
            annotationsManager.deannotate(listOwner, selectedValue.getQualifiedName());
          }
        }.execute();
        return PopupStep.FINAL_CHOICE;
      }

      @NotNull
      public String getTextFor(final PsiAnnotation value) {
        final String qualifiedName = value.getQualifiedName();
        LOG.assertTrue(qualifiedName != null);
        return qualifiedName;
      }
    }).showInBestPositionFor(editor);
  }

  public boolean startInWriteAction() {
    return false;
  }
}