package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


/**
 * @author ven
 */
public class AddAssertStatementFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.AddAssertStatementFix");
  private PsiExpression myExpressionToAssert;

  @NotNull
  public String getName() {
    return InspectionsBundle.message("inspection.assert.quickfix", myExpressionToAssert.getText());
  }

  public AddAssertStatementFix(PsiExpression expressionToAssert) {
    myExpressionToAssert = expressionToAssert;
    LOG.assertTrue(PsiType.BOOLEAN.equals(myExpressionToAssert.getType()));
  }

  public void applyFix(Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PsiStatement anchorStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    LOG.assertTrue(anchorStatement != null);

    @NonNls String text = "assert c;";
    PsiAssertStatement assertStatement;
    try {
      final PsiElementFactory factory = element.getManager().getElementFactory();
      assertStatement = (PsiAssertStatement)factory.createStatementFromText(text, null);
      final PsiExpression assertCondition = assertStatement.getAssertCondition();
      assert assertCondition != null;
      assertCondition.replace(myExpressionToAssert);
      final PsiElement parent = anchorStatement.getParent();
      if (parent instanceof PsiCodeBlock) {
        parent.addBefore(assertStatement, anchorStatement);
      }
      else {
        PsiBlockStatement blockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", null);
        final PsiCodeBlock block = blockStatement.getCodeBlock();
        block.add(assertStatement);
        block.add(anchorStatement);
        anchorStatement.replace(blockStatement);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.quickfix.assert.family");
  }
}
