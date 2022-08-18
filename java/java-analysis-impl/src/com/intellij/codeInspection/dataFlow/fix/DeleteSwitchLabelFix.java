// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    deleteLabelElement(labelElement);
    if (myAddDefaultIfNecessary) {
      IntentionAction addDefaultFix = SwitchBlockHighlightingModel.createAddDefaultFixIfNecessary(block);
      if (addDefaultFix != null) {
        addDefaultFix.invoke(project, editor, file);
      }
    }
  }

  public static void deleteLabelElement(@NotNull PsiCaseLabelElement labelElement) {
    PsiSwitchLabelStatementBase label = PsiImplUtil.getSwitchLabel(labelElement);
    if (label == null) return;
    PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
    if (labelElementList != null && labelElementList.getElementCount() == 1) {
      deleteLabel(label);
    } else {
      new CommentTracker().deleteAndRestoreComments(labelElement);
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
        if (e instanceof PsiDeclarationStatement && nextLabel != null) {
          PsiDeclarationStatement declaration = (PsiDeclarationStatement)e;
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
