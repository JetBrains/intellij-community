package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class AddOnDemandStaticImportAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.AddOnDemandStaticImportAction");

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.on.demand.static.import.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
    if (element == null || LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(element)) > 0) return false;
    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
      if (refExpr.getParent() instanceof PsiReferenceExpression &&
          isParameterizedReference((PsiReferenceExpression)refExpr.getParent())) return false;

      PsiElement resolved = refExpr.resolve();
      if (resolved instanceof PsiClass) {
        String text = CodeInsightBundle.message("intention.add.on.demand.static.import.text", ((PsiClass)resolved).getQualifiedName());
        setText(text);
        return true;
      }
    }

    return false;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
    final PsiClass aClass = (PsiClass)refExpr.resolve();
    PsiImportStaticStatement importStaticStatement = file.getManager().getElementFactory().createImportStaticStatement(aClass, "*");
    ((PsiJavaFile)file).getImportList().add(importStaticStatement);

    PsiFile[] roots = file.getPsiRoots();
    for (PsiFile root : roots) {
      root.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          if (isParameterizedReference(expression)) return;
          PsiExpression qualifierExpression = expression.getQualifierExpression();
          if (qualifierExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifierExpression).isReferenceTo(aClass)) {
            try {
              PsiReferenceExpression copy = (PsiReferenceExpression)expression.copy();
              PsiElement resolved = copy.resolve();
              copy.getQualifierExpression().delete();
              PsiManager manager = expression.getManager();
              if (manager.areElementsEquivalent(copy.resolve(), resolved)) {
                qualifierExpression.delete();
                HighlightManager.getInstance(project).addRangeHighlight(editor, expression.getTextRange().getStartOffset(),
                                                                        expression.getTextRange().getEndOffset(),
                                                                        EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES),
                                                                        false, null);
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
          super.visitElement(expression);
        }
      });
    }
  }

  private static boolean isParameterizedReference(final PsiReferenceExpression expression) {
    return expression.getParameterList() != null && expression.getParameterList().getFirstChild() != null;
  }
}
