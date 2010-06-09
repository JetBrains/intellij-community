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
 * @author ven
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AddSingleMemberStaticImportAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction");
  private static final Key<PsiElement> TEMP_REFERENT_USER_DATA = new Key<PsiElement>("TEMP_REFERENT_USER_DATA");

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.single.member.static.import.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) return false;
    PsiFile file = element.getContainingFile();
    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiReferenceExpression &&
        ((PsiReferenceExpression)element.getParent()).getQualifierExpression() != null) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
      if (refExpr.getParameterList() != null &&
          refExpr.getParameterList().getFirstChild() != null) return false;
      PsiElement resolved = refExpr.resolve();
      if (resolved instanceof PsiMember &&
          ((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass aClass = ((PsiMember)resolved).getContainingClass();
        if (aClass != null && !PsiTreeUtil.isAncestor(aClass, element, true)) {
          String qName = aClass.getQualifiedName();
          if (qName != null) {
            qName = qName + "." +refExpr.getReferenceName();
            if (file instanceof PsiJavaFile) {
              if (((PsiJavaFile)file).getImportList().findSingleImportStatement(refExpr.getReferenceName()) == null) {
                setText(CodeInsightBundle.message("intention.add.single.member.static.import.text", qName));
                return true;
              }
            }
          }
        }
      }
    }

    return false;
  }


  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
    final PsiElement resolved = refExpr.resolve();

    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (refExpr.getReferenceName().equals(expression.getReferenceName())) {
          PsiElement resolved = expression.resolve();
          if (resolved != null) {
            expression.putUserData(TEMP_REFERENT_USER_DATA, resolved);
          }
        }
      }
    });

    ((PsiReferenceExpressionImpl)refExpr).bindToElementViaStaticImport(((PsiMember)resolved).getContainingClass(), ((PsiNamedElement)resolved).getName(), ((PsiJavaFile)file).getImportList());

    file.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.getParameterList() != null &&
            expression.getParameterList().getFirstChild() != null) return;

        if (refExpr.getReferenceName().equals(expression.getReferenceName())) {
          if (!expression.isQualified()) {
            PsiElement referent = expression.getUserData(TEMP_REFERENT_USER_DATA);

            if (referent instanceof PsiMember && referent != expression.resolve()) {
              PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
              try {
                PsiReferenceExpression copy = (PsiReferenceExpression)factory.createExpressionFromText("A." + expression.getReferenceName(), null);
                expression = (PsiReferenceExpression)expression.replace(copy);
                ((PsiReferenceExpression)expression.getQualifierExpression()).bindToElement(((PsiMember)referent).getContainingClass());
              }
              catch (IncorrectOperationException e) {
                LOG.error (e);
              }
            }
            expression.putUserData(TEMP_REFERENT_USER_DATA, null);
          } else {
            if (expression.getQualifierExpression() instanceof PsiReferenceExpression) {
              PsiElement aClass = ((PsiReferenceExpression)expression.getQualifierExpression()).resolve();
              if (aClass == ((PsiMember)resolved).getContainingClass()) {
                try {
                  expression.getQualifierExpression().delete();
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }
            }
          }
          expression.putUserData(TEMP_REFERENT_USER_DATA, null);
        }

        super.visitReferenceExpression(expression);
      }
    });
  }
}