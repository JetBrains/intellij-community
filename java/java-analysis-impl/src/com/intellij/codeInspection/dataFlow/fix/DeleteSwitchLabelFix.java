// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class DeleteSwitchLabelFix implements LocalQuickFix {
  private final String myName;
  private final boolean myBranch;

  public DeleteSwitchLabelFix(PsiSwitchLabelStatement label) {
    myName = Objects.requireNonNull(label.getCaseValue()).getText();
    myBranch = shouldRemoveBranch(label);
  }

  private static boolean shouldRemoveBranch(PsiSwitchLabelStatement label) {
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
  public String getName() {
    return myBranch ?
           "Remove switch branch '" + myName + "'" :
           "Remove switch label '" + myName + "'";
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Remove switch label";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiSwitchLabelStatement label = PsiTreeUtil.getNonStrictParentOfType(descriptor.getStartElement(), PsiSwitchLabelStatement.class);
    if (label == null) return;
    deleteLabel(label);
  }

  public static void deleteLabel(PsiSwitchLabelStatement label) {
    if (shouldRemoveBranch(label)) {
      PsiCodeBlock scope = ObjectUtils.tryCast(label.getParent(), PsiCodeBlock.class);
      if (scope == null) return;
      PsiSwitchLabelStatement nextLabel = PsiTreeUtil.getNextSiblingOfType(label, PsiSwitchLabelStatement.class);
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
          boolean declarationIsReused = Stream.of(elements).anyMatch(element ->
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
