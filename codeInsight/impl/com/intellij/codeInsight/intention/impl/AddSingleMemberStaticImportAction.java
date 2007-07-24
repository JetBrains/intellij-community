/*
 * @author ven
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddSingleMemberStaticImportAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction");
  private static final Key<PsiElement> TEMP_REFERENT_USER_DATA = new Key<PsiElement>("TEMP_REFERENT_USER_DATA");

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.single.member.static.import.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
    if (element == null || LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(element)) > 0) return false;
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

  public void invoke(final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
    final PsiElement resolved = refExpr.resolve();

    file.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (refExpr.getReferenceName().equals(expression.getReferenceName())) {
          PsiElement resolved = expression.resolve();
          if (resolved != null) {
            expression.putUserData(TEMP_REFERENT_USER_DATA, resolved);
          }
        }
      }
    });

    PsiImportStaticStatement importStaticStatement = file.getManager().getElementFactory().createImportStaticStatement(((PsiMember)resolved).getContainingClass(),
                                                                                                                       ((PsiNamedElement)resolved).getName());
    ((PsiJavaFile)file).getImportList().addAfter(importStaticStatement, null);

    file.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.getParameterList() != null &&
            expression.getParameterList().getFirstChild() != null) return;

        if (refExpr.getReferenceName().equals(expression.getReferenceName())) {
          if (!expression.isQualified()) {
            PsiElement referent = expression.getUserData(TEMP_REFERENT_USER_DATA);

            if (referent instanceof PsiMember && referent != expression.resolve()) {
              PsiElementFactory factory = expression.getManager().getElementFactory();
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
        }

        super.visitReferenceExpression(expression);
      }
    });
  }
}