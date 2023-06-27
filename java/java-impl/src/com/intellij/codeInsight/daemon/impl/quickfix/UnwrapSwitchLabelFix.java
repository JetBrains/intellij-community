// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.dataFlow.fix.DeleteSwitchLabelFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonJavaInlineUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class UnwrapSwitchLabelFix extends PsiUpdateModCommandQuickFix {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("remove.unreachable.branches");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiCaseLabelElement label = ObjectUtils.tryCast(element, PsiCaseLabelElement.class);
    if (label == null) return;
    PsiSwitchLabelStatementBase labelStatement = PsiImplUtil.getSwitchLabel(label);
    if (labelStatement == null) return;
    PsiSwitchBlock block = labelStatement.getEnclosingSwitchBlock();
    if (block == null) return;
    List<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.getChildrenOfTypeAsList(block.getBody(), PsiSwitchLabelStatementBase.class);
    boolean shouldKeepDefault = block instanceof PsiSwitchExpression &&
                                !(labelStatement instanceof PsiSwitchLabeledRuleStatement ruleStatement &&
                                  ruleStatement.getBody() instanceof PsiExpressionStatement);
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
        new CommentTracker().deleteAndRestoreComments(labelElement);
      }
    }
  }

  private static void tryUnwrap(@NotNull PsiSwitchLabelStatementBase labelStatement, @NotNull PsiCaseLabelElement label,
                                @NotNull PsiSwitchBlock block) {
    if (block instanceof PsiSwitchStatement switchStatement) {
      BreakConverter converter = BreakConverter.from(block);
      if (converter == null) return;
      converter.process();
      unwrapStatement(labelStatement, label, switchStatement);
    }
    else {
      unwrapExpression((PsiSwitchExpression)block);
    }
  }

  private static void unwrapStatement(@NotNull PsiSwitchLabelStatementBase labelStatement, @NotNull PsiCaseLabelElement label,
                                      @NotNull PsiSwitchStatement switchStatement) {
    PsiCodeBlock block = switchStatement.getBody();
    PsiStatement body = labelStatement instanceof PsiSwitchLabeledRuleStatement ruleStatement ? ruleStatement.getBody() : null;
    List<PsiLocalVariable> variables;
    if (body == null) {
      variables = block != null ? collectVariables(label, switchStatement) : Collections.emptyList();
      new CommentTracker().deleteAndRestoreComments(labelStatement);
    }
    else if (body instanceof PsiBlockStatement blockStatement) {
      variables = collectVariables(label, switchStatement);
      block = blockStatement.getCodeBlock();
    }
    else {
      variables = collectVariables(label, switchStatement);
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
        variables.replaceAll(variable -> addVariable(variable, firstElementAdded, parent));
        inline(variables, parent);
      }
    }
    else if (block != null) {
      PsiBlockStatement element = (PsiBlockStatement)ct.replaceAndRestoreComments(switchStatement, ct.text(block));
      if (!variables.isEmpty()) {
        PsiStatement firstStatement = ArrayUtil.getFirstElement(element.getCodeBlock().getStatements());
        if (firstStatement != null) {
          PsiCodeBlock variableParent = element.getCodeBlock();
          variables.replaceAll(variable -> addVariable(variable, firstStatement, variableParent));
          inline(variables, variableParent);
        }
      }
    }
    else {
      ct.deleteAndRestoreComments(switchStatement);
    }
  }

  private static void inline(@NotNull List<PsiLocalVariable> variables, @NotNull PsiElement variableParent) {
    if (variables.isEmpty()) return;
    var inlineUtil = CommonJavaInlineUtil.getInstance();
    for (int i = variables.size() - 1; i > 0; i--) {
      inline(variables.get(i), inlineUtil);
    }
    PsiLocalVariable firstVariable = variables.get(0);
    if (VariableAccessUtils.isLocalVariableCopy(firstVariable)) {
      inline(firstVariable, inlineUtil);
    }
    else if (!VariableAccessUtils.variableIsUsed(firstVariable, variableParent)) {
      firstVariable.delete();
    }
  }

  private static void inline(@NotNull PsiLocalVariable variable, @NotNull CommonJavaInlineUtil inlineUtil) {
    final PsiExpression initializer = variable.getInitializer();
    assert initializer != null;
    final Collection<PsiReference> references = ReferencesSearch.search(variable).findAll();
    for (PsiReference reference : references) {
      inlineUtil.inlineVariable(variable, initializer, (PsiJavaCodeReferenceElement)reference, null);
    }
    if (!VariableAccessUtils.variableIsAssigned(variable)) {
      variable.delete();
    }
  }

  private static void unwrapExpression(@NotNull PsiSwitchExpression switchExpression) {
    PsiCodeBlock body = switchExpression.getBody();
    if (body == null) return;
    PsiStatement[] statements = body.getStatements();
    if (statements.length != 1 || !(statements[0] instanceof PsiSwitchLabeledRuleStatement rule)) return;
    PsiStatement ruleBody = rule.getBody();
    if (!(ruleBody instanceof PsiExpressionStatement)) return;
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(switchExpression);
    if (surrounder != null) {
      CodeBlockSurrounder.SurroundResult surroundResult = surrounder.surround();
      switchExpression = (PsiSwitchExpression)surroundResult.getExpression();
      rule = (PsiSwitchLabeledRuleStatement)Objects.requireNonNull(switchExpression.getBody()).getStatements()[0];
      ruleBody = rule.getBody();
      if (ruleBody == null) return;
      PsiCaseLabelElement label = Objects.requireNonNull(rule.getCaseLabelElementList()).getElements()[0];
      List<PsiLocalVariable> variables = collectVariables(label, switchExpression);
      if (variables.isEmpty()) {
        new CommentTracker().replaceAndRestoreComments(switchExpression, ((PsiExpressionStatement)ruleBody).getExpression());
        surroundResult.collapse();
        return;
      }
      new CommentTracker().replaceAndRestoreComments(switchExpression, ((PsiExpressionStatement)ruleBody).getExpression());
      variables.replaceAll(variable -> addVariable(variable, surroundResult.getAnchor(), surroundResult.getAnchor().getParent()));
      inline(variables, surroundResult.getAnchor().getParent());
    }
  }

  /**
   * @param label       a switch label element
   * @param switchBlock a considered switch block
   * @return a list of local variables extracted from a pattern variable if it's possible and necessary.
   */
  @NotNull
  private static List<PsiLocalVariable> collectVariables(@NotNull PsiCaseLabelElement label,
                                                         @NotNull PsiSwitchBlock switchBlock) {
    PsiPrimaryPattern pattern = JavaPsiPatternUtil.getTypedPattern(label);
    if (pattern == null) return Collections.emptyList();
    PsiType type = JavaPsiPatternUtil.getPatternType(pattern);
    if (type == null) return Collections.emptyList();
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return Collections.emptyList();
    PsiType selectorType = selector.getType();
    if (selectorType == null) return Collections.emptyList();
    PsiPatternVariable topLevelVariable = JavaPsiPatternUtil.getPatternVariable(pattern);
    String declarationStatementText = type.getPresentableText() + " ";
    if (topLevelVariable != null) {
      declarationStatementText += topLevelVariable.getName() + "=";
    }
    else {
      VariableNameGenerator generator = new VariableNameGenerator(switchBlock, VariableKind.LOCAL_VARIABLE);
      String newVarName = generator.byType(JavaPsiPatternUtil.getPatternType(label)).generate(true);
      declarationStatementText += newVarName + "=";
    }
    if (!type.isAssignableFrom(selectorType)) {
      declarationStatementText += "(" + type.getPresentableText() + ")";
    }
    declarationStatementText += selector.getText() + ";";
    PsiLocalVariable newVariable = createVariable(declarationStatementText, label);
    if (pattern instanceof PsiDeconstructionPattern deconstructionPattern) {
      PsiFile copy = (PsiFile)switchBlock.getContainingFile().copy();
      PsiElement contextCopy = PsiTreeUtil.findSameElementInCopy(switchBlock.getParent(), copy);
      contextCopy.add(newVariable);
      ArrayList<PsiLocalVariable> list = new ArrayList<>();
      list.add(newVariable);
      return collectVariables(deconstructionPattern, newVariable, contextCopy, list);
    }
    return new SmartList<>(newVariable);
  }

  /**
   * @param pattern  record pattern for which component variables are extracted
   * @param variable variable already extracted for the record pattern under consideration
   * @param context  record patterns may not have variables, so it is necessary to generate variable names for such patterns.
   *                 To avoid naming conflicts, this context will be used and extracted variables will be added to it
   * @param result   the list to collect all extracted variables
   * @return a list of local variables extracted from a pattern variable if it's possible and necessary
   */
  private static List<PsiLocalVariable> collectVariables(@NotNull PsiDeconstructionPattern pattern,
                                                         @NotNull PsiLocalVariable variable,
                                                         @NotNull PsiElement context,
                                                         @NotNull List<PsiLocalVariable> result) {
    PsiClassType classType = ObjectUtils.tryCast(pattern.getTypeElement().getType(), PsiClassType.class);
    if (classType == null) return Collections.emptyList();
    PsiClass aClass = classType.resolve();
    if (aClass == null) return Collections.emptyList();
    PsiRecordComponent[] components = aClass.getRecordComponents();
    PsiPattern[] deconstructionComponents = pattern.getDeconstructionList().getDeconstructionComponents();
    if (components.length != deconstructionComponents.length) return Collections.emptyList();
    for (int i = 0; i < deconstructionComponents.length; i++) {
      PsiPattern deconstructionComponent = deconstructionComponents[i];
      PsiPatternVariable patternVariable = JavaPsiPatternUtil.getPatternVariable(deconstructionComponent);
      if (patternVariable == null && !(deconstructionComponent instanceof PsiDeconstructionPattern)) return Collections.emptyList();
      PsiType type = JavaPsiPatternUtil.getPatternType(deconstructionComponent);
      if (type == null) return Collections.emptyList();
      String declarationStatementText = type.getPresentableText() + " ";
      if (patternVariable != null) {
        declarationStatementText += patternVariable.getName() + "=";
      }
      else {
        VariableNameGenerator generator = new VariableNameGenerator(context, VariableKind.LOCAL_VARIABLE);
        String newVarName = generator.byType(type).generate(true);
        declarationStatementText += newVarName + "=";
      }
      if (!type.isAssignableFrom(components[i].getType())) {
        declarationStatementText += "(" + type.getPresentableText() + ")";
      }
      declarationStatementText += variable.getName() + "." + components[i].getName() + "();";
      PsiLocalVariable newVariable = createVariable(declarationStatementText, pattern);
      context.add(newVariable);
      result.add(newVariable);
      if (deconstructionComponent instanceof PsiDeconstructionPattern deconstructionPattern) {
        collectVariables(deconstructionPattern, newVariable, context, result);
      }
    }
    return result;
  }

  private static @NotNull PsiLocalVariable createVariable(@NotNull String declarationStatementText, @NotNull PsiElement context) {
    PsiJavaParserFacade facade = JavaPsiFacade.getInstance(context.getProject()).getParserFacade();
    PsiDeclarationStatement statement = (PsiDeclarationStatement)facade.createStatementFromText(declarationStatementText, context);
    return (PsiLocalVariable)statement.getDeclaredElements()[0];
  }

  /**
   * If there is no naming conflict between <code>variable</code> and existing ones, the name of <code>variable</code> leaves as is.
   * Otherwise, we need to select a new unique name for <code>variable</code> and rename its references as well.
   *
   * @param variable        variable to add
   * @param variableSibling is used as anchor for <code>variable</code> to be added
   * @param variableParent  parent for both <code>variable</code> and <code>variableSibling</code>.
   *                        Mostly used to detect a conflict and to add <code>variable</code> as a child.
   * @return the element which was actually added
   */
  private static @NotNull PsiLocalVariable addVariable(@NotNull PsiLocalVariable variable,
                                                       @NotNull PsiElement variableSibling,
                                                       @NotNull PsiElement variableParent) {
    boolean hasConflictingDeclaration = hasConflictingDeclaration(variable, variableParent);
    PsiStatement declaration = JavaPsiFacade.getInstance(variableSibling.getProject()).getParserFacade()
      .createStatementFromText(variable.getText(), variableSibling);
    variable = (PsiLocalVariable)((PsiDeclarationStatement)variableParent.addBefore(declaration, variableSibling)).getDeclaredElements()[0];
    if (hasConflictingDeclaration) {
      String newVarName = JavaCodeStyleManager.getInstance(variableParent.getProject())
        .suggestUniqueVariableName(variable.getName(), variableParent, true);
      for (PsiReference ref : VariableAccessUtils.getVariableReferences(variable, variableParent)) {
        ref.handleElementRename(newVarName);
      }
      variable.setName(newVarName);
    }
    return variable;
  }


  private static boolean hasConflictingDeclaration(@NotNull PsiLocalVariable variable, @NotNull PsiElement context) {
    return !JavaCodeStyleManager.getInstance(context.getProject()).suggestUniqueVariableName(variable.getName(), context, true)
      .equals(variable.getName());
  }
}
