// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.fixes.CreateDefaultBranchFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.java.codeserver.core.JavaPatternExhaustivenessUtil.hasExhaustivenessError;

public class DeleteSwitchLabelFix extends PsiUpdateModCommandAction<PsiCaseLabelElement> {
  private final String myName;
  private final boolean myAddDefaultIfNecessary;
  private final boolean myBranch;

  public DeleteSwitchLabelFix(@NotNull PsiCaseLabelElement label, boolean addDefaultIfNecessary) {
    super(label);
    myAddDefaultIfNecessary = addDefaultIfNecessary;
    PsiSwitchLabelStatementBase labelStatement = Objects.requireNonNull(PsiImplUtil.getSwitchLabel(label));
    PsiCaseLabelElementList labelElementList = Objects.requireNonNull(labelStatement.getCaseLabelElementList());
    boolean multiple = labelElementList.getElementCount() > 1;
    myBranch = !multiple && shouldRemoveBranch(labelStatement);
    PsiExpression guardExpression = labelStatement.getGuardExpression();
    myName = myBranch && guardExpression != null ? labelStatement.getText()
      .substring(labelElementList.getStartOffsetInParent(), guardExpression.getStartOffsetInParent() +
                                                            guardExpression.getTextLength()) : label.getText();
  }

  private static boolean shouldRemoveBranch(PsiSwitchLabelStatementBase label) {
    if (label instanceof PsiSwitchLabeledRuleStatement) return true;
    PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(label, PsiStatement.class);
    if (nextStatement instanceof PsiSwitchLabelStatement) {
      return false;
    }
    PsiStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(label, PsiStatement.class);
    return prevStatement == null || !ControlFlowUtils.statementMayCompleteNormally(prevStatement);
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiCaseLabelElement element) {
    return Presentation.of(myBranch ?
                           JavaAnalysisBundle.message("remove.switch.branch.0", myName) :
                           JavaAnalysisBundle.message("remove.switch.label.0", myName));
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("remove.switch.label");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiCaseLabelElement labelElement, @NotNull ModPsiUpdater updater) {
    PsiSwitchLabelStatementBase label = PsiImplUtil.getSwitchLabel(labelElement);
    if (label == null) return;
    PsiSwitchBlock block = label.getEnclosingSwitchBlock();
    if (block == null) return;
    deleteLabelElement(context.project(), labelElement);
    if (myAddDefaultIfNecessary && shouldAddDefault(block)) {
      CreateDefaultBranchFix.addDefault(block, updater);
    }
  }

  public static void deleteLabelElement(@NotNull Project project, @NotNull PsiCaseLabelElement labelElement) {
    PsiSwitchLabelStatementBase label = PsiImplUtil.getSwitchLabel(labelElement);
    if (label == null) return;
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    if (labelElementList != null) {
      if (labelElementList.getElementCount() == 1) {
        deleteLabel(label);
        return;
      }
      else if (labelElementList.getElementCount() == 2) {
        PsiElement defaultElement = JavaPsiSwitchUtil.findDefaultElement(label);
        if (defaultElement != null && defaultElement != labelElement) {
          assert JavaKeywords.CASE.equals(label.getFirstChild().getText()) && defaultElement instanceof PsiDefaultCaseLabelElement;
          makeLabelDefault(label, project);
          return;
        }
      }
    }
    new CommentTracker().deleteAndRestoreComments(labelElement);
  }

  private static void makeLabelDefault(@NotNull PsiSwitchLabelStatementBase label, @NotNull Project project) {
    PsiElementFactory factory = PsiElementFactory.getInstance(project);
    if (label instanceof PsiSwitchLabelStatement) {
      PsiSwitchLabelStatementBase defaultLabel = (PsiSwitchLabelStatement)factory.createStatementFromText("default:", null);
      new CommentTracker().replaceAndRestoreComments(label, defaultLabel);
    }
    else if (label instanceof PsiSwitchLabeledRuleStatement rule) {
      PsiSwitchLabeledRuleStatement defaultLabel = (PsiSwitchLabeledRuleStatement)factory.createStatementFromText("default->{}", null);
      PsiStatement body = rule.getBody();
      assert body != null;
      Objects.requireNonNull(defaultLabel.getBody()).replace(body);
      new CommentTracker().replaceAndRestoreComments(label, defaultLabel);
    }
    else {
      assert false;
    }
  }

  public static void deleteLabel(PsiSwitchLabelStatementBase label) {
    if (shouldRemoveBranch(label)) {
      PsiCodeBlock scope = ObjectUtils.tryCast(label.getParent(), PsiCodeBlock.class);
      if (scope == null) return;
      PsiSwitchLabelStatementBase nextLabel = PsiTreeUtil.getNextSiblingOfType(label, PsiSwitchLabelStatementBase.class);
      PsiElement stopAt = nextLabel == null ? scope.getRBrace() : nextLabel;
      while(true) {
        PsiStatement next = PsiTreeUtil.getNextSiblingOfType(nextLabel, PsiStatement.class);
        if (!(next instanceof PsiSwitchLabelStatement)) break;
        nextLabel = (PsiSwitchLabelStatement)next;
      }
      int end = nextLabel == null ? -1 : nextLabel.getTextOffset();
      List<PsiElement> toDelete = new ArrayList<>();
      List<PsiDeclarationStatement> declarations = new ArrayList<>();
      for (PsiElement e = label.getNextSibling(); e != stopAt; e = e.getNextSibling()) {
        if (e instanceof PsiDeclarationStatement declaration && nextLabel != null) {
          PsiElement[] elements = declaration.getDeclaredElements();
          boolean declarationIsReused = ContainerUtil.or(elements, element ->
            ReferencesSearch.search(element, new LocalSearchScope(scope)).anyMatch(ref -> ref.getElement().getTextOffset() > end));
          if (declarationIsReused) {
            StreamEx.of(elements).select(PsiVariable.class).map(PsiVariable::getInitializer).nonNull().into(toDelete);
            declarations.add(declaration);
            continue;
          }
        }
        toDelete.add(e);
      }
      CommentTracker ct = new CommentTracker();
      toDelete.stream().filter(PsiElement::isValid).forEach(ct::delete);
      for (PsiDeclarationStatement declaration : declarations) {
        scope.addAfter(declaration, nextLabel);
        declaration.delete();
      }
      ct.insertCommentsBefore(label);
    }
    new CommentTracker().deleteAndRestoreComments(label);
  }

  public static boolean shouldAddDefault(@NotNull PsiSwitchBlock block) {
    if (!ExpressionUtil.isEnhancedSwitch(block)) return false;
    if (JavaPsiSwitchUtil.getUnconditionalPatternLabel(block) != null) return false;
    if (JavaPsiSwitchUtil.findDefaultElement(block) != null) return false;
    return hasExhaustivenessError(block);
  }
}
