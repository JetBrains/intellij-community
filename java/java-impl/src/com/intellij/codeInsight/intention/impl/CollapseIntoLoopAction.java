// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

public class CollapseIntoLoopAction implements IntentionAction {
  @Override
  public @IntentionName @NotNull String getText() {
    return JavaBundle.message("intention.name.collapse.into.loop");
  }

  @Override
  public @NotNull @IntentionFamilyName String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return LoopModel.from(editor, file) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    LoopModel model = LoopModel.from(editor, file);
    if (model == null) return;
    model.generate();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static final class LoopModel {
    final @NotNull List<PsiExpression> myLoopElements;
    final @NotNull List<PsiExpression> myExpressionsToReplace;
    final @NotNull List<PsiStatement> myStatements;
    final int myStatementCount;
    final @Nullable PsiType myType;

    private LoopModel(@NotNull List<PsiExpression> elements,
                      @NotNull List<PsiExpression> expressionsToReplace,
                      @NotNull List<PsiStatement> statements,
                      int count,
                      @Nullable PsiType type) {
      myLoopElements = elements;
      myExpressionsToReplace = expressionsToReplace;
      myStatements = statements;
      myStatementCount = count;
      myType = type;
    }

    void generate() {
      PsiStatement context = myStatements.get(0);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
      String loopDeclaration;
      String varName;
      if (myType == null) {
        int size = myStatements.size() / myStatementCount;
        varName = new VariableNameGenerator(context, VariableKind.PARAMETER).byType(PsiType.INT).generate(true);
        loopDeclaration = "for(int " + varName + "=0;" + varName + "<" + size + ";" + varName + "++)";
      }
      else {
        varName = new VariableNameGenerator(context, VariableKind.PARAMETER).byType(myType).generate(true);
        loopDeclaration = tryCollapseIntoCountingLoop(varName);
        if (loopDeclaration == null) {
          String container;
          if (myType instanceof PsiPrimitiveType) {
            container = "new " + myType.getCanonicalText() + "[]{" + StringUtil.join(myLoopElements, PsiElement::getText, ",") + "}";
          }
          else {
            container = CommonClassNames.JAVA_UTIL_ARRAYS + ".asList(" + StringUtil.join(myLoopElements, PsiElement::getText, ",") + ")";
          }
          loopDeclaration = "for(" + myType.getCanonicalText() + " " + varName + ":" + container + ")";
        }
      }
      PsiLoopStatement loop = (PsiLoopStatement)factory.createStatementFromText(loopDeclaration + " {}", context);

      PsiCodeBlock block = ((PsiBlockStatement)Objects.requireNonNull(loop.getBody())).getCodeBlock();
      PsiJavaToken brace = Objects.requireNonNull(block.getRBrace());

      PsiExpression ref = factory.createExpressionFromText(varName, context);
      myExpressionsToReplace.forEach(expr -> expr.replace(ref));
      block.addRangeBefore(myStatements.get(0), myStatements.get(myStatementCount - 1), brace);
      PsiElement origBlock = context.getParent();
      JavaCodeStyleManager.getInstance(block.getProject()).shortenClassReferences(origBlock.addBefore(loop, myStatements.get(0)));
      CommentTracker ct = new CommentTracker();
      myLoopElements.forEach(ct::markUnchanged);
      ct.delete(myStatements.subList(myStatementCount, myStatements.size()).toArray(PsiStatement.EMPTY_ARRAY));
      ct.insertCommentsBefore(myStatements.get(0));
      origBlock.deleteChildRange(myStatements.get(0), myStatements.get(myStatementCount - 1));
    }

    private String tryCollapseIntoCountingLoop(String varName) {
      if (!PsiType.INT.equals(myType) && !PsiType.LONG.equals(myType)) return null;
      Long start = null;
      Long step = null;
      Long last = null;
      for (PsiExpression element : myLoopElements) {
        if (!(element instanceof PsiLiteralExpression)) return null;
        Object value = ((PsiLiteralExpression)element).getValue();
        if (!(value instanceof Integer) && !(value instanceof Long)) return null;
        long cur = ((Number)value).longValue();
        if (start == null) {
          start = cur;
        }
        else if (step == null) {
          step = cur - start;
          if (step == 0) return null;
        }
        else if (cur - last != step || (step > 0 && cur < last) || (step < 0 && cur > last)) {
          return null;
        }
        last = cur;
      }
      if (start == null || step == null) return null;
      // Prefer for(int x : new int[] {12, 17}) over for(int x = 12; x <= 17; x+= 5)
      if (myLoopElements.size() == 2 && step != 1L && step != -1L) return null;
      PsiElement parent = myStatements.get(0).getParent();
      boolean mustBeEffectivelyFinal = myExpressionsToReplace.stream()
        .map(ref -> PsiTreeUtil.getParentOfType(ref, PsiClass.class, PsiLambdaExpression.class))
        .anyMatch(ctx -> ctx != null && PsiTreeUtil.isAncestor(parent, ctx, false));
      if (mustBeEffectivelyFinal) return null;
      String suffix = PsiType.LONG.equals(myType) ? "L" : "";
      String initial = myType.getCanonicalText() + " " + varName + "=" + start + suffix;
      String condition =
        varName + (step == 1 && last != (PsiType.LONG.equals(myType) ? Long.MAX_VALUE : Integer.MAX_VALUE) ? "<" + (last + 1) :
                   step == -1 && last != (PsiType.LONG.equals(myType) ? Long.MIN_VALUE : Integer.MIN_VALUE) ? ">" + (last - 1) :
                   (step < 0 ? ">=" : "<=") + last) + suffix;
      String increment = varName + (step == 1 ? "++" : step == -1 ? "--" : step > 0 ? "+=" + step + suffix : "-=" + (-step) + suffix);
      return "for(" + initial + ";" + condition + ";" + increment + ")";
    }

    private static @NotNull List<PsiStatement> extractStatements(PsiFile file, SelectionModel model) {
      int startOffset = model.getSelectionStart();
      int endOffset = model.getSelectionEnd();
      PsiElement[] elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
      return StreamEx.of(elements)
        .map(e -> tryCast(e, PsiStatement.class))
        .collect(MoreCollectors.ifAllMatch(LoopModel::isAllowedStatement, Collectors.toList()))
        .orElse(Collections.emptyList());
    }

    private static @NotNull List<PsiStatement> extractStatements(PsiFile file, int offset) {
      PsiElement pos = file.findElementAt(offset);
      PsiStatement statement = PsiTreeUtil.getParentOfType(pos, PsiStatement.class, false, PsiMember.class, PsiCodeBlock.class);
      if (statement == null) return Collections.emptyList();
      return StreamEx.iterate(statement, LoopModel::isAllowedStatement,
                              st -> PsiTreeUtil.getNextSiblingOfType(st, PsiStatement.class)).toList();
    }

    static @Nullable LoopModel from(Editor editor, PsiFile file) {
      if (!(file instanceof PsiJavaFile) || !PsiUtil.isLanguageLevel5OrHigher(file)) return null;
      SelectionModel selectionModel = editor.getSelectionModel();
      boolean mayTrimTail;
      List<PsiStatement> statements;
      if (selectionModel.hasSelection()) {
        mayTrimTail = false;
        statements = extractStatements(file, selectionModel);
      } else {
        mayTrimTail = true;
        statements = extractStatements(file, editor.getCaretModel().getOffset());
      }
      int size = statements.size();
      if (size <= 1 || size > (mayTrimTail ? 100 : 1000)) return null;
      if (!(statements.get(0).getParent() instanceof PsiCodeBlock)) return null;
      for (int count = 1; count <= size / 2; count++) {
        if (!mayTrimTail && size % count != 0) continue;
        LoopModel model = from(statements, count, mayTrimTail);
        if (model != null) {
          return model;
        }
      }
      return null;
    }

    private static @Nullable LoopModel from(List<PsiStatement> statements, int count, boolean mayTrimTail) {
      int size = statements.size();
      List<PsiExpression> expressionsToReplace = new ArrayList<>();
      List<PsiExpression> expressionsToIterate = new ArrayList<>();
      int offset;
      for (offset = count; offset + count <= size; offset += count) {
        if (!tryConsumeIteration(statements, count, offset, expressionsToReplace, expressionsToIterate)) {
          if (!mayTrimTail || offset == count) return null;
          break;
        }
      }
      statements = statements.subList(0, offset);
      PsiType type = expressionsToIterate.isEmpty() ? null : expressionsToIterate.get(0).getType();
      return new LoopModel(expressionsToIterate, expressionsToReplace, statements, count, type);
    }

    private static boolean tryConsumeIteration(@NotNull List<PsiStatement> statements,
                                               int count,
                                               int offset,
                                               @NotNull List<PsiExpression> expressionsToReplace,
                                               @NotNull List<PsiExpression> expressionsToIterate) {
      EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
      PsiExpression firstIterationExpression = null;
      PsiExpression curIterationExpression = null;
      boolean secondIteration = count == offset;
      int mismatchedStatements = 0;
      for (int index = 0; index < count; index++) {
        PsiStatement first = statements.get(index);
        PsiStatement cur = statements.get(index + offset);
        EquivalenceChecker.Match match = new TrackingEquivalenceChecker().statementsMatch(first, cur);
        if (match.isExactMismatch()) return false;
        if (match.isExactMatch()) continue;
        mismatchedStatements++;
        PsiElement leftDiff = match.getLeftDiff();
        PsiElement rightDiff = match.getRightDiff();
        if (!(leftDiff instanceof PsiExpression) || !(rightDiff instanceof PsiExpression)) return false;
        curIterationExpression = (PsiExpression)rightDiff;
        firstIterationExpression = (PsiExpression)leftDiff;
        if (PsiUtil.isAccessedForWriting(curIterationExpression)) return false;
        PsiType curType = curIterationExpression.getType();
        PsiType firstType = firstIterationExpression.getType();
        if (curType == null || !curType.equals(firstType)) return false;
        Set<PsiVariable> usedVariables;
        if (secondIteration) {
          if (!expressionsToReplace.isEmpty()) {
            PsiExpression firstExpressionToReplace = expressionsToReplace.get(0);
            if (!equivalence.expressionsAreEquivalent(firstExpressionToReplace, firstIterationExpression)) return false;
            if (!firstType.equals(firstExpressionToReplace.getType())) return false;
          }
          expressionsToReplace.add(firstIterationExpression);
          usedVariables = StreamEx.of(firstIterationExpression, curIterationExpression)
            .map(VariableAccessUtils::collectUsedVariables).toFlatCollection(Function.identity(), HashSet::new);
        }
        else {
          if (!expressionsToReplace.contains(firstIterationExpression)) return false;
          usedVariables = VariableAccessUtils.collectUsedVariables(curIterationExpression);
        }
        if (!usedVariables.isEmpty() &&
            statements.subList(0, count).stream().anyMatch(st -> VariableAccessUtils.isAnyVariableAssigned(usedVariables, st))) {
          return false;
        }
      }
      if (secondIteration) {
        ContainerUtil.addIfNotNull(expressionsToIterate, firstIterationExpression);
      } else {
        if (mismatchedStatements != expressionsToReplace.size()) {
          return false;
        }
      }
      ContainerUtil.addIfNotNull(expressionsToIterate, curIterationExpression);
      return true;
    }

    private static boolean isAllowedStatement(PsiStatement st) {
      return st != null && !ControlFlowUtils.statementContainsNakedBreak(st) && !ControlFlowUtils.statementContainsNakedContinue(st);
    }
  }
}
