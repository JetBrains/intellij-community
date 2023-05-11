// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInsight.daemon.impl.analysis.SwitchBlockHighlightingModel;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DeleteSwitchLabelFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myName;
  private final boolean myAddDefaultIfNecessary;
  private final boolean myBranch;

  public DeleteSwitchLabelFix(@NotNull PsiCaseLabelElement label, boolean addDefaultIfNecessary) {
    super(label);
    myName = label.getText();
    myAddDefaultIfNecessary = addDefaultIfNecessary;
    PsiSwitchLabelStatementBase labelStatement = Objects.requireNonNull(PsiImplUtil.getSwitchLabel(label));
    PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
    boolean multiple = labelElementList != null && labelElementList.getElementCount() > 1;
    myBranch = !multiple && shouldRemoveBranch(labelStatement);
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

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    return myBranch ?
           JavaAnalysisBundle.message("remove.switch.branch.0", myName) :
           JavaAnalysisBundle.message("remove.switch.label.0", myName);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("remove.switch.label");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    PsiCaseLabelElement labelElement = ObjectUtils.tryCast(startElement, PsiCaseLabelElement.class);
    if (labelElement == null) return;
    PsiSwitchLabelStatementBase label = PsiImplUtil.getSwitchLabel(labelElement);
    if (label == null) return;
    PsiSwitchBlock block = label.getEnclosingSwitchBlock();
    if (block == null) return;
    deleteLabelElement(project, labelElement);
    if (myAddDefaultIfNecessary) {
      IntentionAction addDefaultFix = SwitchBlockHighlightingModel.createAddDefaultFixIfNecessary(block);
      if (addDefaultFix != null) {
        addDefaultFix.invoke(project, editor, file);
      }
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
        PsiElement defaultElement = SwitchUtils.findDefaultElement(label);
        if (defaultElement != null && defaultElement != labelElement) {
          assert PsiKeyword.CASE.equals(label.getFirstChild().getText()) && defaultElement instanceof PsiDefaultCaseLabelElement;
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
}
