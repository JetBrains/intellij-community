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
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeannotateIntentionAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#" + DeannotateIntentionAction.class.getName());
  private String myAnnotationName = null;

  @Override
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("deannotate.intention.action.text") + (myAnnotationName != null ? " " + myAnnotationName : "");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("deannotate.intention.action.text");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiModifierListOwner listOwner = getContainer(editor, file);
    if (listOwner != null) {
      final PsiAnnotation[] annotations = ExternalAnnotationsManager.getInstance(project).findExternalAnnotations(listOwner);
      if (annotations != null) {
        if (annotations.length == 1) {
          myAnnotationName = annotations[0].getQualifiedName();
        }
        return true;
      }
    }
    return false;
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

  @Override
  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiModifierListOwner listOwner = getContainer(editor, file);
    final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    final PsiAnnotation[] externalAnnotations = annotationsManager.findExternalAnnotations(listOwner);
    JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<PsiAnnotation>(CodeInsightBundle.message("deannotate.intention.chooser.title"), externalAnnotations) {
      @Override
      public PopupStep onChosen(final PsiAnnotation selectedValue, final boolean finalChoice) {
        new WriteCommandAction(project){
          @Override
          protected void run(final Result result) throws Throwable {
            final VirtualFile virtualFile = file.getVirtualFile();
            if (annotationsManager.deannotate(listOwner, selectedValue.getQualifiedName()) && virtualFile != null && virtualFile.isInLocalFileSystem()) {
              UndoUtil.markPsiFileForUndo(file);
            }
          }
        }.execute();
        return PopupStep.FINAL_CHOICE;
      }

      @Override
      @NotNull
      public String getTextFor(final PsiAnnotation value) {
        final String qualifiedName = value.getQualifiedName();
        LOG.assertTrue(qualifiedName != null);
        return qualifiedName;
      }
    }).showInBestPositionFor(editor);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}