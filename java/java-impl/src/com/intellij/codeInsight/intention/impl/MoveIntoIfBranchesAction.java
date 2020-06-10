// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

public class MoveIntoIfBranchesAction implements IntentionAction {
  @Override
  public @IntentionName @NotNull String getText() {
    return JavaBundle.message("intention.name.move.into.if.branches");
  }

  @Override
  public @NotNull @IntentionFamilyName String getFamilyName() {
    return getText();
  }

  private static List<PsiStatement> extractStatements(Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return Collections.emptyList();
    SelectionModel model = editor.getSelectionModel();
    if (!model.hasSelection()) {
      int offset = editor.getCaretModel().getOffset();
      PsiElement pos = file.findElementAt(offset);
      PsiStatement statement = PsiTreeUtil.getParentOfType(pos, PsiStatement.class, false, PsiMember.class, PsiCodeBlock.class);
      return statement == null ? Collections.emptyList() : Collections.singletonList(statement);
    }
    int startOffset = model.getSelectionStart();
    int endOffset = model.getSelectionEnd();
    PsiElement[] elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    return StreamEx.of(elements)
      .map(e -> tryCast(e, PsiStatement.class))
      .collect(MoreCollectors.ifAllMatch(Objects::nonNull, Collectors.toList()))
      .orElse(Collections.emptyList());
  }

  private static boolean hasConflictingDeclarations(@NotNull PsiIfStatement ifStatement, @NotNull List<PsiStatement> statements) {
    PsiStatement lastStatement = statements.get(statements.size() - 1);
    List<PsiElement> afterLast = new ArrayList<>();
    for (PsiElement e = lastStatement.getNextSibling(); e != null; e = e.getNextSibling()) {
      if (!(e instanceof PsiComment) && !(e instanceof PsiWhiteSpace)) {
        afterLast.add(e);
      }
    }
    if (afterLast.isEmpty()) return false;
    Set<PsiNamedElement> declared = StreamEx.of(statements).select(PsiDeclarationStatement.class)
      .flatArray(PsiDeclarationStatement::getDeclaredElements).select(PsiNamedElement.class).toSet();
    if (declared.isEmpty()) return false;
    if (SyntaxTraverser.psiTraverser().withRoots(afterLast).filter(PsiJavaCodeReferenceElement.class)
          .filter(ref -> declared.contains(ref.resolve())).first() != null) {
      return true;
    }
    return StreamEx.of(ifStatement.getThenBranch(), ifStatement.getElseBranch()).flatArray(ControlFlowUtils::unwrapBlock)
      .select(PsiDeclarationStatement.class).flatArray(PsiDeclarationStatement::getDeclaredElements)
      .select(PsiNamedElement.class).map(PsiNamedElement::getName).nonNull()
      .anyMatch(name -> ContainerUtil.exists(declared, d -> name.equals(d.getName())));
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    List<PsiStatement> statements = extractStatements(editor, file);
    if (statements.isEmpty()) return false;
    PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(statements.get(0));
    return prev instanceof PsiIfStatement && !hasConflictingDeclarations((PsiIfStatement)prev, statements);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    List<PsiStatement> statements = extractStatements(editor, file);
    if (statements.isEmpty()) return;
    PsiIfStatement ifStatement = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsBackward(statements.get(0)), PsiIfStatement.class);
    if (ifStatement == null || hasConflictingDeclarations(ifStatement, statements)) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch == null) {
      ifStatement.setThenBranch(factory.createStatementFromText("{}", null));
      thenBranch = Objects.requireNonNull(ifStatement.getThenBranch());
    }
    else if (!(thenBranch instanceof PsiBlockStatement)) {
      thenBranch = (PsiStatement)BlockUtils.expandSingleStatementToBlockStatement(thenBranch).getParent().getParent();
    }
    PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch == null) {
      ifStatement.setElseBranch(factory.createStatementFromText("{}", null));
      elseBranch = Objects.requireNonNull(ifStatement.getElseBranch());
    }
    else if (!(elseBranch instanceof PsiBlockStatement)) {
      elseBranch = (PsiStatement)BlockUtils.expandSingleStatementToBlockStatement(elseBranch).getParent().getParent();
    }
    PsiCodeBlock thenBlock = ((PsiBlockStatement)thenBranch).getCodeBlock();
    PsiCodeBlock elseBlock = ((PsiBlockStatement)elseBranch).getCodeBlock();
    PsiJavaToken thenBrace = thenBlock.getRBrace();
    PsiJavaToken elseBrace = elseBlock.getRBrace();
    if (thenBrace == null || elseBrace == null) return;
    thenBlock.addRangeBefore(statements.get(0), statements.get(statements.size() - 1), thenBrace);
    elseBlock.addRangeBefore(statements.get(0), statements.get(statements.size() - 1), elseBrace);
    ifStatement.getParent().deleteChildRange(statements.get(0), statements.get(statements.size() - 1));
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
