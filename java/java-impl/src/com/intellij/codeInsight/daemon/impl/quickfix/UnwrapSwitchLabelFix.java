// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.fix.DeleteSwitchLabelFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import com.siyeh.ig.psiutils.BreakConverter;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class UnwrapSwitchLabelFix implements LocalQuickFix {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("remove.unreachable.branches");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiCaseLabelElement label = ObjectUtils.tryCast(descriptor.getStartElement(), PsiCaseLabelElement.class);
    if (label == null) return;
    PsiSwitchLabelStatementBase labelStatement = PsiImplUtil.getSwitchLabel(label);
    if (labelStatement == null) return;
    PsiSwitchBlock block = labelStatement.getEnclosingSwitchBlock();
    if (block == null) return;
    List<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.getChildrenOfTypeAsList(block.getBody(), PsiSwitchLabelStatementBase.class);
    boolean shouldKeepDefault = block instanceof PsiSwitchExpression &&
                                !(labelStatement instanceof PsiSwitchLabeledRuleStatement &&
                                  ((PsiSwitchLabeledRuleStatement)labelStatement).getBody() instanceof PsiExpressionStatement);
    for (PsiSwitchLabelStatementBase otherLabel : labels) {
      if (otherLabel == labelStatement || (shouldKeepDefault && otherLabel.isDefaultCase())) continue;
      DeleteSwitchLabelFix.deleteLabel(otherLabel);
    }
    for (PsiCaseLabelElement labelElement : Objects.requireNonNull(labelStatement.getCaseLabelElementList()).getElements()) {
      if (labelElement != label) {
        new CommentTracker().deleteAndRestoreComments(labelElement);
      }
    }
    tryUnwrap(labelStatement, label, block);
  }

  private static void tryUnwrap(@NotNull PsiSwitchLabelStatementBase labelStatement, @NotNull PsiCaseLabelElement label,
                                @NotNull PsiSwitchBlock block) {
    if (block instanceof PsiSwitchStatement) {
      BreakConverter converter = BreakConverter.from(block);
      if (converter == null) return;
      converter.process();
      unwrapStatement(labelStatement, label, (PsiSwitchStatement)block);
    }
    else {
      unwrapExpression((PsiSwitchExpression)block, label);
    }
  }

  private static void unwrapStatement(@NotNull PsiSwitchLabelStatementBase labelStatement, @NotNull PsiCaseLabelElement label,
                                      @NotNull PsiSwitchStatement switchStatement) {
    PsiCodeBlock block = switchStatement.getBody();
    PsiStatement body =
      labelStatement instanceof PsiSwitchLabeledRuleStatement ? ((PsiSwitchLabeledRuleStatement)labelStatement).getBody() : null;
    PsiLocalVariable variable = null;
    if (body == null) {
      if (block != null) {
        variable = createVariable(label, switchStatement, block);
      }
      new CommentTracker().deleteAndRestoreComments(labelStatement);
    }
    else if (body instanceof PsiBlockStatement) {
      variable = createVariable(label, switchStatement, body);
      block = ((PsiBlockStatement)body).getCodeBlock();
    }
    else {
      variable = createVariable(label, switchStatement, body);
      new CommentTracker().replaceAndRestoreComments(labelStatement, body);
    }
    PsiCodeBlock parent = ObjectUtils.tryCast(switchStatement.getParent(), PsiCodeBlock.class);
    CommentTracker ct = new CommentTracker();
    if (parent != null && !BlockUtils.containsConflictingDeclarations(Objects.requireNonNull(block), parent)) {
      ct.grabComments(switchStatement);
      ct.markUnchanged(block);
      ct.insertCommentsBefore(switchStatement);
      PsiElement firstElementAdded = BlockUtils.inlineCodeBlock(switchStatement, block);
      if (variable != null && firstElementAdded != null) {
        addVariable(variable, firstElementAdded, parent);
      }
    }
    else if (block != null) {
      if (variable != null) {
        PsiStatement firstStatement = ArrayUtil.getFirstElement(block.getStatements());
        if (firstStatement != null) {
          block.addBefore(variable, firstStatement);
        }
      }
      ct.replaceAndRestoreComments(switchStatement, ct.text(block));
    }
    else {
      ct.deleteAndRestoreComments(switchStatement);
    }
  }

  /**
   * Unwraps switch expression if it consists of single expression-branch; does nothing otherwise
   *
   * @param switchExpression expression to unwrap
   */
  public static void unwrapExpression(@NotNull PsiSwitchExpression switchExpression) {
    unwrapExpression(switchExpression, null);
  }

  private static void unwrapExpression(@NotNull PsiSwitchExpression switchExpression, @Nullable PsiCaseLabelElement label) {
    PsiCodeBlock body = switchExpression.getBody();
    if (body == null) return;
    PsiStatement[] statements = body.getStatements();
    if (statements.length != 1 || !(statements[0] instanceof PsiSwitchLabeledRuleStatement)) return;
    PsiSwitchLabeledRuleStatement rule = (PsiSwitchLabeledRuleStatement)statements[0];
    PsiStatement ruleBody = rule.getBody();
    if (!(ruleBody instanceof PsiExpressionStatement)) return;
    if (label == null) {
      new CommentTracker().replaceAndRestoreComments(switchExpression, ((PsiExpressionStatement)ruleBody).getExpression());
      return;
    }
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(switchExpression);
    if (surrounder != null) {
      CodeBlockSurrounder.SurroundResult surroundResult = surrounder.surround();
      switchExpression = (PsiSwitchExpression)surroundResult.getExpression();
      rule = (PsiSwitchLabeledRuleStatement)Objects.requireNonNull(switchExpression.getBody()).getStatements()[0];
      ruleBody = rule.getBody();
      if (ruleBody == null) return;
      label = Objects.requireNonNull(rule.getCaseLabelElementList()).getElements()[0];
      PsiLocalVariable variable = createVariable(label, switchExpression, ruleBody);
      if (variable == null) {
        new CommentTracker().replaceAndRestoreComments(switchExpression, ((PsiExpressionStatement)ruleBody).getExpression());
        return;
      }
      new CommentTracker().replaceAndRestoreComments(switchExpression, ((PsiExpressionStatement)ruleBody).getExpression());
      addVariable(variable, surroundResult.getAnchor(), surroundResult.getAnchor().getParent());
    }
  }

  /**
   * @param label       a switch label element
   * @param switchBlock a considered switch block
   * @param body        a body of either switch labeled rule if <code>switchBlock</code> consists of labeled rules,
   *                    or a body of the entire switch statement
   * @return a local variable extracted from a pattern variable if it's possible and necessary.
   * If a pattern variable type is not total for selector type, a type cast expression will be created then.
   */
  @Nullable
  private static PsiLocalVariable createVariable(@NotNull PsiCaseLabelElement label,
                                                 @NotNull PsiSwitchBlock switchBlock,
                                                 @NotNull PsiElement body) {
    if (!(label instanceof PsiPattern)) return null;
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return null;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return null;
    PsiPatternVariable patternVar = JavaPsiPatternUtil.getPatternVariable(((PsiPattern)label));
    if (patternVar == null) return null;
    JBIterable<PsiReferenceExpression> bodyTraverser = SyntaxTraverser.psiTraverser(body).filter(PsiReferenceExpression.class);
    PsiSwitchLabelStatementBase labelStatement = PsiTreeUtil.getParentOfType(label, PsiSwitchLabelStatementBase.class);
    if (labelStatement instanceof PsiSwitchLabelStatement) {
      bodyTraverser = bodyTraverser.filter(expr -> PsiTreeUtil.getParentOfType(expr, PsiSwitchLabelStatementBase.class) != labelStatement);
    }
    if (bodyTraverser.find(expr -> expr.resolve() == patternVar) == null) return null;
    String declarationStatementText = Objects.requireNonNull(patternVar.getPattern()).getText() + "=";
    if (!JavaPsiPatternUtil.isTotalForType(patternVar.getPattern(), selectorType)) {
      declarationStatementText += "(" + patternVar.getTypeElement().getType().getPresentableText() + ")";
    }
    declarationStatementText += selector.getText() + ";";
    return (PsiLocalVariable)((PsiDeclarationStatement)JavaPsiFacade.getInstance(label.getProject()).getParserFacade()
      .createStatementFromText(declarationStatementText, label)).getDeclaredElements()[0];
  }

  /**
   * If there is no naming conflict between <code>variable</code> and existing ones, the name of <code>variable</code> leaves as is.
   * Otherwise, we need to select a new unique name for <code>variable</code> and rename its references as well.
   *
   * @param variable        variable to add
   * @param variableSibling is used as anchor for <code>variable</code> to be added
   * @param variableParent  parent for both <code>variable</code> and <code>variableSibling</code>.
   *                        Mostly used to detect a conflict and to add <code>variable</code> as a child.
   */
  private static void addVariable(@NotNull PsiLocalVariable variable, @NotNull PsiElement variableSibling, @NotNull PsiElement variableParent) {
    boolean hasConflictingDeclaration = hasConflictingDeclaration(variable, variableParent);
    PsiStatement declaration = JavaPsiFacade.getInstance(variableSibling.getProject()).getParserFacade()
      .createStatementFromText(variable.getText(), variableSibling);
    variable = (PsiLocalVariable)((PsiDeclarationStatement)variableParent.addBefore(declaration, variableSibling)).getDeclaredElements()[0];
    if (!hasConflictingDeclaration) return;
    String newVarName = JavaCodeStyleManager.getInstance(variableParent.getProject())
      .suggestUniqueVariableName(variable.getName(), variableParent, true);
    for (PsiReference ref : VariableAccessUtils.getVariableReferences(variable, variableParent)) {
      ref.handleElementRename(newVarName);
    }
    variable.setName(newVarName);
  }

  private static boolean hasConflictingDeclaration(@NotNull PsiLocalVariable variable, @NotNull PsiElement context) {
    return !JavaCodeStyleManager.getInstance(context.getProject()).suggestUniqueVariableName(variable.getName(), context, true)
      .equals(variable.getName());
  }
}
