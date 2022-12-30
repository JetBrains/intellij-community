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
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
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
      if (otherLabel == labelStatement) continue;
      if (!shouldKeepDefault || !SwitchUtils.isDefaultLabel(otherLabel)) {
        DeleteSwitchLabelFix.deleteLabel(otherLabel);
      }
      else {
        deleteLabelsExceptDefault(otherLabel);
      }
    }
    for (PsiCaseLabelElement labelElement : Objects.requireNonNull(labelStatement.getCaseLabelElementList()).getElements()) {
      if (labelElement != label && !(shouldKeepDefault && labelElement instanceof PsiDefaultCaseLabelElement)) {
        new CommentTracker().deleteAndRestoreComments(labelElement);
      }
    }
    tryUnwrap(labelStatement, label, block);
  }

  private static void deleteLabelsExceptDefault(@NotNull PsiSwitchLabelStatementBase label) {
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    if (labelElementList != null) {
      for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
        if (labelElement instanceof PsiDefaultCaseLabelElement) continue;
        new CommentTracker().deleteAndRestoreComments(labelElement);      }
    }
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
    List<PsiLocalVariable> variables = Collections.emptyList();
    if (body == null) {
      if (block != null) {
        variables = createVariables(label, switchStatement, block);
      }
      new CommentTracker().deleteAndRestoreComments(labelStatement);
    }
    else if (body instanceof PsiBlockStatement) {
      variables = createVariables(label, switchStatement, body);
      block = ((PsiBlockStatement)body).getCodeBlock();
    }
    else {
      variables = createVariables(label, switchStatement, body);
      new CommentTracker().replaceAndRestoreComments(labelStatement, body);
    }
    PsiCodeBlock parent = ObjectUtils.tryCast(switchStatement.getParent(), PsiCodeBlock.class);
    CommentTracker ct = new CommentTracker();
    if (parent != null && !BlockUtils.containsConflictingDeclarations(Objects.requireNonNull(block), parent)) {
      ct.grabComments(switchStatement);
      ct.markUnchanged(block);
      ct.insertCommentsBefore(switchStatement);
      PsiElement firstElementAdded = BlockUtils.inlineCodeBlock(switchStatement, block);
      if (firstElementAdded != null) {
        for (PsiLocalVariable variable : variables) {
          addVariable(variable, firstElementAdded, parent);
        }
      }
    }
    else if (block != null) {
      if (!variables.isEmpty()) {
        PsiStatement firstStatement = ArrayUtil.getFirstElement(block.getStatements());
        if (firstStatement != null) {
          for (PsiLocalVariable variable : variables) {
            block.addBefore(variable, firstStatement);
          }
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
      List<PsiLocalVariable> variables = createVariables(label, switchExpression, ruleBody);
      if (variables.isEmpty()) {
        new CommentTracker().replaceAndRestoreComments(switchExpression, ((PsiExpressionStatement)ruleBody).getExpression());
        surroundResult.collapse();
        return;
      }
      new CommentTracker().replaceAndRestoreComments(switchExpression, ((PsiExpressionStatement)ruleBody).getExpression());
      for (PsiLocalVariable variable : variables) {
        addVariable(variable, surroundResult.getAnchor(), surroundResult.getAnchor().getParent());
      }
    }
  }

  /**
   * @param label       a switch label element
   * @param switchBlock a considered switch block
   * @param body        a body of either switch labeled rule if <code>switchBlock</code> consists of labeled rules,
   *                    or a body of the entire switch statement
   * @return a list of local variables extracted from a pattern variable if it's possible and necessary.
   * If a pattern variable type is not total for selector type, a type cast expression will be created then.
   */
  @NotNull
  private static List<PsiLocalVariable> createVariables(@NotNull PsiCaseLabelElement label,
                                                        @NotNull PsiSwitchBlock switchBlock,
                                                        @NotNull PsiElement body) {
    if (!(label instanceof PsiPattern)) return Collections.emptyList();
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return Collections.emptyList();
    PsiType selectorType = selector.getType();
    if (selectorType == null) return Collections.emptyList();
    String selectorText = selector.getText();
    VariablesCreator variablesCreator;
    if (label instanceof PsiDeconstructionPattern) {
      variablesCreator = new DeconstructionPatternVariablesCreator((PsiDeconstructionPattern)label, selectorType, selectorText, body);
      if (!variablesCreator.myPatterns.hasNext()) return Collections.emptyList();
    }
    else {
      variablesCreator = new PatternVariablesCreator((PsiPattern)label, selectorType, selectorText, body);
    }
    return variablesCreator.createVariables();
  }

  abstract static class VariablesCreator {

    private final @NotNull PsiPattern myLabel;
    protected final @NotNull String mySelectorText;
    protected final @NotNull PsiType mySelectorType;
    protected final @NotNull Iterator<PsiPattern> myPatterns;
    private final @NotNull PsiElement myBody;

    protected VariablesCreator(@NotNull PsiPattern label,
                               @NotNull PsiType selectorType,
                               @NotNull String selectorText,
                               @NotNull Iterator<PsiPattern> patterns, @NotNull PsiElement body) {
      myLabel = label;
      mySelectorText = selectorText;
      mySelectorType = selectorType;
      myPatterns = patterns;
      myBody = body;
    }

    @NotNull
    public SmartList<PsiLocalVariable> createVariables() {
      JBIterable<PsiReferenceExpression> bodyTraverser = SyntaxTraverser.psiTraverser(myBody).filter(PsiReferenceExpression.class);
      PsiSwitchLabelStatementBase labelStatement = PsiTreeUtil.getParentOfType(myLabel, PsiSwitchLabelStatementBase.class);
      if (labelStatement instanceof PsiSwitchLabelStatement) {
        bodyTraverser =
          bodyTraverser.filter(expr -> PsiTreeUtil.getParentOfType(expr, PsiSwitchLabelStatementBase.class) != labelStatement);
      }
      SmartList<PsiLocalVariable> result = new SmartList<>();
      while (myPatterns.hasNext()) {
        PsiPatternVariable patternVar = JavaPsiPatternUtil.getPatternVariable(myPatterns.next());
        PsiLocalVariable variable = createVariable(myLabel, bodyTraverser, patternVar);
        ContainerUtil.addIfNotNull(result, variable);
      }
      return result;
    }

    @Nullable
    private PsiLocalVariable createVariable(@NotNull PsiCaseLabelElement label,
                                            @NotNull JBIterable<PsiReferenceExpression> bodyTraverser,
                                            @Nullable PsiPatternVariable patternVar) {
      if (patternVar == null) return null;
      if (bodyTraverser.find(expr -> expr.resolve() == patternVar) == null) return null;
      String declarationStatementText = Objects.requireNonNull(patternVar.getPattern()).getText() + "=" + getVariableAssigment(patternVar);
      return (PsiLocalVariable)((PsiDeclarationStatement)JavaPsiFacade.getInstance(label.getProject()).getParserFacade()
        .createStatementFromText(declarationStatementText, label)).getDeclaredElements()[0];
    }

    @NotNull
    abstract String getVariableAssigment(PsiPatternVariable patternVar);
  }

  static class DeconstructionPatternVariablesCreator extends VariablesCreator {
    private final @NotNull Iterator<PsiRecordComponent> myRecordComponents;

    DeconstructionPatternVariablesCreator(@NotNull PsiDeconstructionPattern pattern,
                                          @NotNull PsiType selectorType,
                                          @NotNull String selectorText,
                                          @NotNull PsiElement body) {
      super(pattern, selectorType, selectorText, ContainerUtil.iterate(getDeconstructionComponents(pattern)), body);
      PsiClass aClass = ((PsiClassType)selectorType).resolve();
      if (aClass != null) {
        PsiRecordComponent[] recordComponents = aClass.getRecordComponents();
        if (getDeconstructionComponents(pattern).length == recordComponents.length) {
          myRecordComponents = ContainerUtil.iterate(recordComponents);
          return;
        }
      }
      myPatterns.forEachRemaining((ignore) -> {});
      myRecordComponents = Collections.emptyIterator();
    }

    private static @NotNull PsiPattern @NotNull [] getDeconstructionComponents(@NotNull PsiDeconstructionPattern pattern) {
      return pattern.getDeconstructionList().getDeconstructionComponents();
    }

    @Override
    @NotNull
    String getVariableAssigment(PsiPatternVariable ignore) {
      return mySelectorText + "." + myRecordComponents.next().getName() + "();";
    }
  }

  static class PatternVariablesCreator extends VariablesCreator {

    PatternVariablesCreator(@NotNull PsiPattern pattern,
                            @NotNull PsiType selectorType,
                            @NotNull String selectorText,
                            @NotNull PsiElement body) {
      super(pattern, selectorType, selectorText, Collections.singleton(pattern).iterator(), body);
    }

    @Override
    @NotNull
    String getVariableAssigment(PsiPatternVariable patternVar) {
      String typeCastIfNeeded;
      if (!JavaPsiPatternUtil.isTotalForType(patternVar.getPattern(), mySelectorType)) {
        typeCastIfNeeded = "(" + patternVar.getTypeElement().getType().getPresentableText() + ")";
      }
      else {
        typeCastIfNeeded = "";
      }
      return typeCastIfNeeded + mySelectorText + ";";
    }
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
