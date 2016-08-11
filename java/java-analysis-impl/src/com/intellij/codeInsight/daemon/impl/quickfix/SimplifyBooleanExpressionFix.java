/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SimplifyBooleanExpressionFix extends LocalQuickFixOnPsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpression");
  public static final String FAMILY_NAME = QuickFixBundle.message("simplify.boolean.expression.family");

  private final boolean mySubExpressionValue;

  // subExpressionValue == Boolean.TRUE or Boolean.FALSE if subExpression evaluates to boolean constant and needs to be replaced
  //   otherwise subExpressionValue= null and we starting to simplify expression without any further knowledge
  public SimplifyBooleanExpressionFix(@NotNull PsiExpression subExpression, boolean subExpressionValue) {
    super(subExpression);
    mySubExpressionValue = subExpressionValue;
  }

  @Override
  @NotNull
  public String getText() {
    PsiExpression expression = getSubExpression();
    assert expression != null;
    if (PsiUtil.skipParenthesizedExprUp(expression.getParent()) instanceof PsiIfStatement) {
      return mySubExpressionValue ? "Unwrap 'if' statement" : "Remove 'if' statement";
    }
    return QuickFixBundle.message("simplify.boolean.expression.text", expression.getText(),  mySubExpressionValue);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return FAMILY_NAME;
  }

  @Override
  public boolean isAvailable() {
    PsiExpression expression = getSubExpression();
    return super.isAvailable()
           && expression != null
           && expression.getManager().isInProject(expression)
           && !PsiUtil.isAccessedForWriting(expression);
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    if (!isAvailable()) return;
    final PsiExpression expression = getSubExpression();
    if (!FileModificationService.getInstance().preparePsiElementForWrite(expression)) return;
    ApplicationManager.getApplication().runWriteAction(() -> simplifyExpression(project, expression, mySubExpressionValue));
  }

  public static void simplifyExpression(Project project, final PsiExpression subExpression, final Boolean subExpressionValue) {
    PsiExpression expression;
    if (subExpressionValue == null) {
      expression = subExpression;
    }
    else {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiExpression constExpression = factory.createExpressionFromText(Boolean.toString(subExpressionValue.booleanValue()), subExpression);
      expression = (PsiExpression)subExpression.replace(constExpression);
    }
    while (expression.getParent() instanceof PsiExpression) {
      expression = (PsiExpression)expression.getParent();
    }
    simplifyExpression(expression);
  }

  public static boolean simplifyIfStatement(final PsiExpression expression) throws IncorrectOperationException {
    PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiIfStatement) || ((PsiIfStatement)parent).getCondition() != expression) return false;
    if (!(expression instanceof PsiLiteralExpression) || !PsiType.BOOLEAN.equals(expression.getType())) return false;
    boolean condition = Boolean.parseBoolean(expression.getText());
    PsiIfStatement ifStatement = (PsiIfStatement)parent;
    if (condition) {
      replaceWithStatements(ifStatement, ifStatement.getThenBranch());
    }
    else {
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch == null) {
        ifStatement.delete();
      }
      else {
        replaceWithStatements(ifStatement, elseBranch);
      }
    }
    return true;
  }

  private static void replaceWithStatements(final PsiIfStatement orig, final PsiStatement statement) throws IncorrectOperationException {
    if (statement == null) {
      orig.delete();
      return;
    }
    PsiElement parent = orig.getParent();
    if (parent == null) return;
    if (statement instanceof PsiBlockStatement && parent instanceof PsiCodeBlock &&
        !DeclarationSearchUtils.containsConflictingDeclarations(((PsiBlockStatement)statement).getCodeBlock(), (PsiCodeBlock)parent)) {
      // See IDEADEV-24277
      // Code block can only be inlined into another (parent) code block.
      // Code blocks, which are if or loop statement branches should not be inlined.
      PsiCodeBlock codeBlock = ((PsiBlockStatement)statement).getCodeBlock();
      PsiJavaToken lBrace = codeBlock.getLBrace();
      PsiJavaToken rBrace = codeBlock.getRBrace();
      if (lBrace == null || rBrace == null) return;


      final PsiElement[] children = codeBlock.getChildren();
      if (children.length > 2) {
        final PsiElement added =
          parent.addRangeBefore(
            children[1],
            children[children.length - 2],
            orig);
        final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(orig.getManager());
        codeStyleManager.reformat(added);
      }
      orig.delete();
    }
    else {
      orig.replace(statement);
    }
  }

  public static void simplifyExpression(PsiExpression expression) throws IncorrectOperationException {
    final PsiExpression[] result = {(PsiExpression)expression.copy()};
    final ExpressionVisitor expressionVisitor = new ExpressionVisitor(expression.getManager(), true);
    final IncorrectOperationException[] exception = {null};
    result[0].accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        // read in all children in advance since due to element replacement involving its siblings invalidation
        PsiElement[] children = element.getChildren();
        for (PsiElement child : children) {
          child.accept(this);
        }
      }

      @Override
      public void visitExpression(PsiExpression expression) {
        super.visitExpression(expression);
        expressionVisitor.clear();
        expression.accept(expressionVisitor);
        if (expressionVisitor.resultExpression != null) {
          LOG.assertTrue(expressionVisitor.resultExpression.isValid());
          try {
            if (expression != result[0]) {
              expression.replace(expressionVisitor.resultExpression);
            }
            else {
              result[0] = expressionVisitor.resultExpression;
            }
          }
          catch (IncorrectOperationException e) {
            exception[0] = e;
          }
        }
      }
    });
    if (exception[0] != null) {
      throw exception[0];
    }
    PsiExpression newExpression = (PsiExpression)expression.replace(result[0]);
    if (newExpression instanceof PsiLiteralExpression) {
      final PsiElement parent = newExpression.getParent();
      if (parent instanceof PsiAssertStatement && ((PsiLiteralExpression)newExpression).getValue() == Boolean.TRUE) {
        parent.delete();
        return;
      }
    }
    if (!simplifyIfStatement(newExpression)) {
      ParenthesesUtils.removeParentheses(newExpression, false);
    }
  }

  public static boolean canBeSimplified(@NotNull PsiExpression expression) {
    if (!(expression instanceof PsiConditionalExpression) && !PsiType.BOOLEAN.equals(expression.getType())) return false;

    final ExpressionVisitor expressionVisitor = new ExpressionVisitor(expression.getManager(), false);
    final Ref<Boolean> canBeSimplified = new Ref<>(Boolean.FALSE);
    expression.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (!canBeSimplified.get().booleanValue()) {
          super.visitElement(element);
        }
      }

      @Override
      public void visitExpression(PsiExpression expression) {
        super.visitExpression(expression);
        expressionVisitor.clear();
        expression.accept(expressionVisitor);
        if (expressionVisitor.canBeSimplifiedFlag) {
          canBeSimplified.set(Boolean.TRUE);
        }
      }
    });
    return canBeSimplified.get().booleanValue();
  }

  private PsiExpression getSubExpression() {
    PsiElement element = getStartElement();
    return element instanceof PsiExpression ? (PsiExpression)element : null;
  }

  private static class ExpressionVisitor extends JavaElementVisitor {
    private PsiExpression resultExpression;
    private final PsiExpression trueExpression;
    private final PsiExpression falseExpression;
    private final boolean isCreateResult;
    boolean canBeSimplifiedFlag;

    private ExpressionVisitor(PsiManager psiManager, final boolean createResult) {
      isCreateResult = createResult;
      trueExpression = createResult ? createExpression(psiManager, Boolean.toString(true)) : null;
      falseExpression = createResult ? createExpression(psiManager, Boolean.toString(false)) : null;
    }

    private static PsiExpression createExpression(final PsiManager psiManager, @NonNls String text) {
      try {
        return JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createExpressionFromText(text, null);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }

    private boolean markAndCheckCreateResult() {
      canBeSimplifiedFlag = true;
      return isCreateResult;
    }

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      PsiExpression[] operands = expression.getOperands();
      PsiExpression lExpr = operands[0];
      IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.XOR == tokenType) {

        boolean negate = false;
        List<PsiExpression> expressions = new ArrayList<>();
        for (PsiExpression operand : operands) {
          final Boolean constBoolean = getConstBoolean(operand);
          if (constBoolean != null) {
            markAndCheckCreateResult();
            if (constBoolean == Boolean.TRUE) {
              negate = !negate;
            }
            continue;
          }
          expressions.add(operand);
        }
        if (expressions.isEmpty()) {
          resultExpression = negate ? trueExpression : falseExpression;
        } else {
          String simplifiedText = StringUtil.join(expressions, expression1 -> expression1.getText(), " ^ ");
          if (negate) {
            if (expressions.size() > 1) {
              simplifiedText = "!(" + simplifiedText + ")";
            } else {
              simplifiedText = "!" + simplifiedText;
            }
          }
          resultExpression = JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(simplifiedText, expression);
        }
      } else {
        for (int i = 1; i < operands.length; i++) {
          Boolean l = getConstBoolean(lExpr);
          PsiExpression operand = operands[i];
          Boolean r = getConstBoolean(operand);
          if (l != null) {
            simplifyBinary(tokenType, l, operand);
          }
          else if (r != null) {
            simplifyBinary(tokenType, r, lExpr);
          }
          else {
            final PsiJavaToken javaToken = expression.getTokenBeforeOperand(operand);
            if (javaToken != null && !PsiTreeUtil.hasErrorElements(operand) && !PsiTreeUtil.hasErrorElements(lExpr)) {
              try {
                resultExpression = JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(lExpr.getText() + javaToken.getText() + operand.getText(), expression);
              }
              catch (IncorrectOperationException e) {
                resultExpression = null;
              }
            }
            else {
              resultExpression = null;
            }
          }
          if (resultExpression != null) {
            lExpr = resultExpression;
          }
        }
      }
    }

    private void simplifyBinary(IElementType tokenType, Boolean lConstBoolean, PsiExpression rOperand) {
      if (!markAndCheckCreateResult()) {
        return;
      }
      if (JavaTokenType.ANDAND == tokenType || JavaTokenType.AND == tokenType) {
        resultExpression = lConstBoolean.booleanValue() ? rOperand : falseExpression;
      }
      else if (JavaTokenType.OROR == tokenType || JavaTokenType.OR == tokenType) {
        resultExpression = lConstBoolean.booleanValue() ? trueExpression : rOperand;
      }
      else if (JavaTokenType.EQEQ == tokenType) {
        simplifyEquation(lConstBoolean, rOperand);
      }
      else if (JavaTokenType.NE == tokenType) {
        PsiPrefixExpression negatedExpression = createNegatedExpression(rOperand);
        resultExpression = negatedExpression;
        visitPrefixExpression(negatedExpression);
        simplifyEquation(lConstBoolean, resultExpression);
      }
    }

    private void simplifyEquation(Boolean constBoolean, PsiExpression otherOperand) {
      if (constBoolean.booleanValue()) {
        resultExpression = otherOperand;
      }
      else {
        PsiPrefixExpression negated = createNegatedExpression(otherOperand);
        resultExpression = negated;
        visitPrefixExpression(negated);
      }
    }

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      Boolean condition = getConstBoolean(expression.getCondition());
      if (condition == null) return;
      if (!markAndCheckCreateResult()) {
        return;
      }
      resultExpression = condition.booleanValue() ? expression.getThenExpression() : expression.getElseExpression();
    }

    private static PsiPrefixExpression createNegatedExpression(PsiExpression otherOperand) {
      PsiPrefixExpression expression = (PsiPrefixExpression)createExpression(otherOperand.getManager(), "!(xxx)");
      assert expression != null;
      PsiExpression operand = expression.getOperand();
      assert operand != null;
      try {
        operand.replace(otherOperand);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      return expression;
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      PsiExpression operand = expression.getOperand();
      Boolean constBoolean = getConstBoolean(operand);
      if (constBoolean == null) return;
      IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.EXCL == tokenType) {
        if (!markAndCheckCreateResult()) {
          return;
        }
        resultExpression = constBoolean.booleanValue() ? falseExpression : trueExpression;
      }
    }


    @Override
    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      PsiExpression subExpr = expression.getExpression();
      Boolean constBoolean = getConstBoolean(subExpr);
      if (constBoolean == null) return;
      if (!markAndCheckCreateResult()) {
        return;
      }
      resultExpression = constBoolean.booleanValue() ? trueExpression : falseExpression;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public void clear() {
      resultExpression = null;
    }
  }

  public static Boolean getConstBoolean(PsiExpression operand) {
    if (operand == null) return null;
    operand = PsiUtil.deparenthesizeExpression(operand);
    if (operand == null) return null;
    String text = operand.getText();
    return PsiKeyword.TRUE.equals(text) ? Boolean.TRUE : PsiKeyword.FALSE.equals(text) ? Boolean.FALSE : null;
  }
}
