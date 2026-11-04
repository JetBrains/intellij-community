// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.generation.surroundWith.JavaWithIfSurrounder;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchExpression;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiYieldStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ipp.trivialif.MergeIfAndIntention;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SurroundWithIfFix extends PsiUpdateModCommandQuickFix {
  private static final Logger LOG = Logger.getInstance(SurroundWithIfFix.class);
  private final String myText;
  private final String mySuffix;

  @Override
  public @NotNull String getName() {
    return JavaBundle.message("inspection.surround.if.quickfix", myText, mySuffix);
  }

  public SurroundWithIfFix(@NotNull PsiExpression expressionToAssert, String suffix) {
    myText = ParenthesesUtils.getText(expressionToAssert, ParenthesesUtils.BINARY_AND_PRECEDENCE);
    mySuffix = suffix;
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiElement anchorStatement = CommonJavaRefactoringUtil.getParentStatement(element, false);
    LOG.assertTrue(anchorStatement != null);
    if (anchorStatement.getParent() instanceof PsiLambdaExpression) {
      final PsiCodeBlock body = CommonJavaRefactoringUtil.expandExpressionLambdaToCodeBlock((PsiLambdaExpression)anchorStatement.getParent());
      anchorStatement = body.getStatements()[0];
    }
    if (!(anchorStatement instanceof PsiBlockStatement) && anchorStatement.getParent() instanceof PsiSwitchLabeledRuleStatement rule) {
      // A rule body cannot be an if statement
      PsiCodeBlock body = SwitchUtils.wrapRuleBodyIntoCodeBlock(rule);
      LOG.assertTrue(body != null);
      anchorStatement = body.getStatements()[0];
    }
    PsiElement[] elements = {anchorStatement};
    PsiElement prev = PsiTreeUtil.skipWhitespacesBackward(anchorStatement);
    if (prev instanceof PsiComment && JavaSuppressionUtil.getSuppressedInspectionIdsIn(prev) != null) {
      elements = new PsiElement[] {prev, anchorStatement};
    }
    ResultContext contextBefore = ResultContext.from(anchorStatement);
    boolean mayCompleteNormallyBefore = contextBefore == null || contextBefore.mayCompleteNormally();
    PsiIfStatement ifStatement = new JavaWithIfSurrounder()
      .surroundStatements(project, anchorStatement.getParent(), elements, myText + mySuffix);
    ResultContext contextAfter = mayCompleteNormallyBefore ? null : ResultContext.from(ifStatement);
    PsiExpression defaultResult = null;
    if (contextAfter != null && contextAfter.mayCompleteNormally()) {
      PsiStatement resultStatement = contextAfter.addDefaultResultAfter(ifStatement);
      // BlockUtils.addAfter can replace the if statement with a copy inside a new code block
      PsiElement prevStatement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(resultStatement);
      while (prevStatement instanceof PsiLabeledStatement labeled) {
        prevStatement = labeled.getStatement();
      }
      ifStatement = prevStatement instanceof PsiIfStatement prevIf ? prevIf : null;
      defaultResult = resultStatement instanceof PsiReturnStatement returnStatement ? returnStatement.getReturnValue() :
                      resultStatement instanceof PsiYieldStatement yieldStatement ? yieldStatement.getExpression() : null;
    }
    if (ifStatement != null) {
      MergeIfAndIntention.mergeWithNestedIf(ifStatement);
    }
    if (defaultResult != null) {
      updater.templateBuilder().field(defaultResult, defaultResult.getText());
    }
  }

  /**
   * The code that must produce a result of {@code type} with a {@code keyword} statement.
   * The code is wrong when its {@code region} may complete normally.
   *
   * @param region the code block or statement that must not complete normally
   * @param type the type of the result
   * @param keyword the "return" or "yield" keyword
   */
  private record ResultContext(@NotNull PsiElement region, @NotNull PsiType type, @NotNull String keyword) {
    boolean mayCompleteNormally() {
      return region instanceof PsiCodeBlock block ? ControlFlowUtils.codeBlockMayCompleteNormally(block) :
             ControlFlowUtils.statementMayCompleteNormally((PsiStatement)region);
    }

    @NotNull PsiStatement addDefaultResultAfter(@NotNull PsiStatement anchor) {
      String text = keyword + " " + PsiTypesUtil.getDefaultValueOfType(type) + ";";
      PsiStatement newStatement = JavaPsiFacade.getElementFactory(anchor.getProject()).createStatementFromText(text, anchor);
      return BlockUtils.addAfter(anchor, newStatement);
    }

    static @Nullable ResultContext from(@NotNull PsiElement element) {
      PsiElement owner = PsiTreeUtil.getParentOfType(element, PsiMember.class, PsiLambdaExpression.class, PsiSwitchExpression.class);
      if (owner instanceof PsiMethod method) {
        return create(method.getBody(), method.getReturnType(), JavaKeywords.RETURN);
      }
      if (owner instanceof PsiLambdaExpression lambda) {
        return create(lambda.getBody(), LambdaUtil.getFunctionalInterfaceReturnType(lambda), JavaKeywords.RETURN);
      }
      if (owner instanceof PsiSwitchExpression switchExpression) {
        PsiCodeBlock body = switchExpression.getBody();
        if (body == null || !PsiTreeUtil.isAncestor(body, element, true)) return null;
        PsiElement topStatement = element;
        while (topStatement.getParent() != body) {
          topStatement = topStatement.getParent();
        }
        if (topStatement instanceof PsiSwitchLabeledRuleStatement rule) {
          return create(rule.getBody(), switchExpression.getType(), JavaKeywords.YIELD);
        }
        // Only the last statement of a statement group cannot fall through to the next group
        PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(topStatement, PsiStatement.class);
        if (nextStatement == null || nextStatement instanceof PsiSwitchLabelStatement) {
          return create(topStatement, switchExpression.getType(), JavaKeywords.YIELD);
        }
      }
      return null;
    }

    private static @Nullable ResultContext create(@Nullable PsiElement region, @Nullable PsiType type, @NotNull String keyword) {
      if (!(region instanceof PsiCodeBlock || region instanceof PsiStatement) ||
          type == null || PsiTypes.voidType().equals(type)) {
        return null;
      }
      return new ResultContext(region, type, keyword);
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("inspection.surround.if.family");
  }

  public static boolean isAvailable(PsiExpression qualifier) {
    if (!qualifier.isValid() || qualifier.getText() == null) {
      return false;
    }
    PsiStatement statement = PsiTreeUtil.getParentOfType(qualifier, PsiStatement.class);
    if (statement == null) return false;
    PsiElement parent = statement.getParent();
    return !(parent instanceof PsiForStatement);
  }
}
