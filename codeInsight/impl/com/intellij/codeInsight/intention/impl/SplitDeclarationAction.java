package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class SplitDeclarationAction extends BaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.split.declaration.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    if (!(element instanceof PsiJavaToken)) return false;
    if (element instanceof PsiCompiledElement) return false;
    if (!file.getManager().isInProject(file)) return false;

    final PsiElement context = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class, PsiClass.class);
    if (context instanceof PsiDeclarationStatement) {
      return isAvaliableOnDeclarationStatement((PsiDeclarationStatement)context, element);
    }

    PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (field != null && PsiTreeUtil.getParentOfType(element, PsiDocComment.class) == null && isAvaliableOnField(field)) {
      setText(CodeInsightBundle.message("intention.split.declaration.text"));
      return true;
    }
    return false;
  }

  private static boolean isAvaliableOnField(PsiField field) {
    final PsiTypeElement typeElement = field.getTypeElement();
    if (typeElement == null) return false;
    if (PsiTreeUtil.getParentOfType(typeElement, PsiField.class) != field) return true;

    PsiElement nextField = field.getNextSibling();
    while (nextField != null && !(nextField instanceof PsiField)) nextField = nextField.getNextSibling();

    if (nextField != null && ((PsiField) nextField).getTypeElement() == typeElement) return true;

    return false;
  }

  private boolean isAvaliableOnDeclarationStatement(PsiDeclarationStatement decl, PsiElement element) {
    PsiElement[] declaredElements = decl.getDeclaredElements();
    if (declaredElements.length == 1) {
      if (!(declaredElements[0] instanceof PsiLocalVariable)) return false;
      PsiLocalVariable var = (PsiLocalVariable) declaredElements[0];
      if (var.getInitializer() == null) return false;
      PsiTypeElement type = var.getTypeElement();
      if (PsiTreeUtil.isAncestor(type, element, false) ||
          element.getParent() == var && ((PsiJavaToken)element).getTokenType() != JavaTokenType.SEMICOLON) {
        setText(CodeInsightBundle.message("intention.split.declaration.assignment.text"));
        return true;
      }
    } else if (declaredElements.length > 1) {
      if (decl.getParent() instanceof PsiForStatement) return false;

      for (PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiLocalVariable)) return false;
        PsiLocalVariable var = (PsiLocalVariable)declaredElement;
        PsiTypeElement type = var.getTypeElement();
        if (PsiTreeUtil.isAncestor(type, element, false) || element == var.getNameIdentifier()) {
          setText(CodeInsightBundle.message("intention.split.declaration.text"));
          return true;
        }
      }
    }

    return false;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    PsiManager psiManager = PsiManager.getInstance(project);
    int offset = editor.getCaretModel().getOffset();

    PsiElement token = file.findElementAt(offset);
    PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(
        token,
        PsiDeclarationStatement.class
    );

    if (decl != null) {
      invokeOnDeclarationStatement(decl, psiManager, project);
    }
    else {
      PsiField field = PsiTreeUtil.getParentOfType(token, PsiField.class);
      if (field != null) {
        field.normalizeDeclaration();
      }
    }
  }

  private static void invokeOnDeclarationStatement(PsiDeclarationStatement decl, PsiManager psiManager,
                                                            Project project) throws IncorrectOperationException {
    if (decl.getDeclaredElements().length == 1) {
      PsiLocalVariable var = (PsiLocalVariable) decl.getDeclaredElements()[0];
      var.normalizeDeclaration();
      PsiExpressionStatement statement = (PsiExpressionStatement) psiManager.getElementFactory()
          .createStatementFromText(var.getName() + "=xxx;", null);
      statement = (PsiExpressionStatement) CodeStyleManager.getInstance(project).reformat(statement);
      PsiAssignmentExpression assignment = (PsiAssignmentExpression) statement.getExpression();
      PsiExpression initializer = var.getInitializer();
      PsiExpression rExpression;
      if (initializer instanceof PsiArrayInitializerExpression) {
        rExpression = psiManager.getElementFactory().createExpressionFromText(
            "new " + var.getTypeElement().getText() + " " + initializer.getText(), null
        );
      }
      else {
        rExpression = initializer;
      }
      assignment.getRExpression().replace(rExpression);
      initializer.delete();

      PsiElement block = decl.getParent();
      if (block instanceof PsiForStatement) {
        block.getParent().addBefore(
            psiManager.getElementFactory().createVariableDeclarationStatement(var.getName(),
                                                                              var.getType(),
                                                                              null),
            block);
        decl.replace(statement);
      } else {
        block.addAfter(statement, decl);
      }
    } else {
      ((PsiLocalVariable) decl.getDeclaredElements()[0]).normalizeDeclaration();
    }
  }
}
