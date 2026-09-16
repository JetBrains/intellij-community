// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

public final class MoveIntoIfBranchesAction implements ModCommandAction {
  @Override
  public @NotNull @IntentionFamilyName String getFamilyName() {
    return JavaBundle.message("intention.name.move.into.if.branches");
  }

  private static @Unmodifiable List<PsiStatement> extractStatements(@NotNull ActionContext context) {
    PsiFile file = context.file();
    if (!(file instanceof PsiJavaFile)) return Collections.emptyList();
    TextRange selection = context.selection();
    if (selection.isEmpty()) {
      int offset = context.offset();
      PsiElement pos = file.findElementAt(offset);
      PsiStatement statement = PsiTreeUtil.getParentOfType(pos, PsiStatement.class, false, PsiMember.class, PsiCodeBlock.class);
      return ContainerUtil.createMaybeSingletonList(statement);
    }
    int startOffset = selection.getStartOffset();
    int endOffset = selection.getEndOffset();
    PsiElement[] elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    return StreamEx.of(elements)
      .map(e -> tryCast(e, PsiStatement.class))
      .collect(MoreCollectors.ifAllMatch(Objects::nonNull, Collectors.toList()))
      .orElse(Collections.emptyList());
  }

  private static boolean hasConflictingDeclarations(@NotNull PsiIfStatement ifStatement, @NotNull List<PsiStatement> statements) {
    PsiStatement lastStatement = statements.getLast();
    List<PsiElement> afterLast = new ArrayList<>();
    for (PsiElement e = lastStatement.getNextSibling(); e != null; e = e.getNextSibling()) {
      if (!(e instanceof PsiComment) && !(e instanceof PsiWhiteSpace)) {
        afterLast.add(e);
      }
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    PsiStatement elseBranch = ifStatement.getElseBranch();
    if (!ControlFlowUtils.statementMayCompleteNormally(thenBranch) ||
        !ControlFlowUtils.statementMayCompleteNormally(elseBranch)) {
      return true;
    }
    Set<String> declaredInIf = getDeclaredInStatement(thenBranch, elseBranch);
    if (afterLast.isEmpty() && declaredInIf.isEmpty()) return false;
    if (collectEffectiveQualifiers(statements, declaredInIf).containsValue(null)) return true;
    Set<PsiVariable> declared = StreamEx.of(statements).flatCollection(VariableAccessUtils::findDeclaredVariables).toSet();
    if (declared.isEmpty()) return false;
    if (SyntaxTraverser.psiTraverser().withRoots(afterLast).filter(PsiJavaCodeReferenceElement.class)
          .filter(ref -> declared.contains(ref.resolve())).first() != null) {
      return true;
    }
    return ContainerUtil.exists(declaredInIf, name -> ContainerUtil.exists(declared, d -> name.equals(d.getName())));
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    if (!BaseIntentionAction.canModify(context.file())) return null;
    List<PsiStatement> statements = extractStatements(context);
    if (statements.isEmpty()) return null;
    PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(statements.getFirst());
    if (!(prev instanceof PsiIfStatement ifStatement) || hasConflictingDeclarations(ifStatement, statements)) return null;
    return Presentation.of(getFamilyName());
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return ModCommand.psiUpdate(context.file(), f -> invoke(context.withFile(f)));
  }
  
  private static void invoke(@NotNull ActionContext context) {
    List<PsiStatement> statements = extractStatements(context);
    if (statements.isEmpty()) return;
    PsiIfStatement ifStatement = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsBackward(statements.getFirst()), PsiIfStatement.class);
    if (ifStatement == null || hasConflictingDeclarations(ifStatement, statements)) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
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

    Set<String> declaredIfStatements = getDeclaredInStatement(thenBranch);
    Set<String> declaredElseStatements = getDeclaredInStatement(elseBranch);

    Map<String, PsiExpression> qualifiers =
      collectEffectiveQualifiers(statements, ContainerUtil.union(declaredIfStatements, declaredElseStatements));
    PsiElement thenNewStatement = thenBlock.addRangeBefore(statements.getFirst(), statements.getLast(), thenBrace);
    PsiElement elseNewStatement = elseBlock.addRangeBefore(statements.getFirst(), statements.getLast(), elseBrace);
    qualifyIfNeeded(qualifiers, declaredIfStatements, thenNewStatement, thenBrace, factory);
    qualifyIfNeeded(qualifiers, declaredElseStatements, elseNewStatement, elseBrace, factory);
    ifStatement.getParent().deleteChildRange(statements.getFirst(), statements.getLast());
  }

  /// Qualifies references in the newly created statements if there is shadowing to a locally declared variable
  ///
  /// @param qualifiers           map of variables and their effective qualifiers 
  /// @param declaredInStatements names of variables created inside an if/else block 
  /// @param newElementStart      first newly inserted statement
  /// @param newElementEnd        end of if/else block, expected to be a right brace
  private static void qualifyIfNeeded(@NotNull Map<String, PsiExpression> qualifiers,
                                      @NotNull Set<String> declaredInStatements,
                                      @NotNull PsiElement newElementStart,
                                      @NotNull PsiElement newElementEnd,
                                      @NotNull PsiElementFactory factory) {
    if (declaredInStatements.isEmpty()) return;

    PsiClass containingClass = PsiUtil.getContainingClass(newElementStart);
    if (containingClass == null) return;
    List<PsiElement> newRange = PsiTreeUtil.getElementsOfRange(newElementStart, newElementEnd);

    SyntaxTraverser.psiTraverser()
      .withRoots(newRange)
      .filter(PsiReferenceExpression.class)
      .forEach(ref -> {
        PsiLocalVariable var = shouldBeRequalified(ref, PsiLocalVariable.class, declaredInStatements, containingClass);
        if (var != null) {
          ref.replace(factory.createExpressionFromText(qualifiers.get(var.getName()).getText() + "." + var.getName(), var));
        }
      });
  }

  /// {@return a map of variable names associated with their effective qualifiers}
  ///
  /// @param declaredInStatements names of the variables we are interested in, meaning we collect effective qualifiers only for [PsiField]s
  ///                             sharing the same names.
  /// @param statements           statements to visit
  private static Map<String, PsiExpression> collectEffectiveQualifiers(List<PsiStatement> statements, Set<String> declaredInStatements) {
    PsiClass containingClass = PsiUtil.getContainingClass(statements.getFirst());
    if (containingClass == null) {
      return Collections.EMPTY_MAP;
    }
    Map<String, PsiExpression> results = HashMap.newHashMap(2);

    SyntaxTraverser.psiTraverser()
      .withRoots(statements)
      .filter(PsiReferenceExpression.class)
      .forEach(ref -> {
        PsiField field = shouldBeRequalified(ref, PsiField.class, declaredInStatements, containingClass);
        if (field != null) {
          results.put(field.getName(), ExpressionUtils.getEffectiveQualifier(ref));
        }
      });
    return results;
  }

  /// {@return the resolved `ref` if it should be requalified, `null` otherwise}
  private static <T extends PsiVariable> @Nullable T shouldBeRequalified(@NotNull PsiReferenceExpression ref,
                                                                         @NotNull Class<T> targetClass,
                                                                         @NotNull Set<String> declaredInStatements,
                                                                         @NotNull PsiClass containingClass) {
    if (ref.getQualifierExpression() != null) return null;
    T resolved = tryCast(ref.resolve(), targetClass);

    if (resolved == null ||
        resolved.isUnnamed() ||
        !declaredInStatements.contains(resolved.getName()) ||
        PsiUtil.isVariableNameUnique(Objects.requireNonNull(resolved.getName()), containingClass)) {
      return null;
    }
    return resolved;
  }

  /// @return The names of declared variables inside the `branchStatements` inputs
  private static Set<String> getDeclaredInStatement(PsiStatement... branchStatements) {
    return StreamEx.of(branchStatements).flatArray(ControlFlowUtils::unwrapBlock)
      .select(PsiDeclarationStatement.class).flatArray(PsiDeclarationStatement::getDeclaredElements)
      .select(PsiNamedElement.class).map(PsiNamedElement::getName).nonNull().toSet();
  }
}
