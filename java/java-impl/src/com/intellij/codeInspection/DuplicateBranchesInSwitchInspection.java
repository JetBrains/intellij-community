// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.refactoring.util.duplicates.ReturnValue;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.siyeh.ig.migration.TryWithIdenticalCatchesInspection.collectCommentTexts;

/**
 * @author Pavel.Dolgov
 */
public class DuplicateBranchesInSwitchInspection extends LocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new DuplicateBranchesVisitor(holder);
  }

  private static class DuplicateBranchesVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    DuplicateBranchesVisitor(ProblemsHolder holder) {myHolder = holder;}

    @Override
    public void visitSwitchStatement(PsiSwitchStatement switchStatement) {
      super.visitSwitchStatement(switchStatement);

      List<Branch> branches = collectBranches(switchStatement);
      int size = branches.size();
      if (size > 1) {
        boolean[] isDuplicate = new boolean[size];
        for (int i = 0; i < size - 1; i++) {
          if (isDuplicate[i]) continue;

          for (int j = i + 1; j < size; j++) {
            if (areDuplicates(branches, i, j)) {
              isDuplicate[j] = true;
              registerProblem(branches.get(j).myStatements);

              if (!isDuplicate[i]) {
                isDuplicate[i] = true;
                registerProblem(branches.get(i).myStatements);
              }
            }
          }
        }
      }
    }

    private void registerProblem(@NotNull PsiStatement[] statements) {
      ProblemDescriptor descriptor = InspectionManager.getInstance(myHolder.getProject())
        .createProblemDescriptor(statements[0], statements[statements.length - 1],
                                 InspectionsBundle.message("inspection.duplicate.branches.in.switch.message"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myHolder.isOnTheFly());
      myHolder.registerProblem(descriptor);
    }
  }

  @NotNull
  static List<Branch> collectBranches(@NotNull PsiSwitchStatement switchStatement) {
    PsiCodeBlock body = switchStatement.getBody();
    if (body == null) return Collections.emptyList();

    List<Branch> branches = new ArrayList<>();
    List<PsiStatement> statementList = null;
    Comments comments = new Comments();

    for (PsiElement child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiSwitchLabelStatement) {
        PsiSwitchLabelStatement switchLabel = (PsiSwitchLabelStatement)child;
        if (statementList != null) {
          branches.add(new Branch(statementList, hasImplicitBreak(switchLabel), comments.fetchTexts()));
          statementList = null;
        }
        comments.addFrom(switchLabel);
      }
      else if (child instanceof PsiStatement) {
        PsiStatement statement = (PsiStatement)child;
        if (statementList == null) {
          statementList = new ArrayList<>();
        }
        statementList.add(statement);
        comments.addFrom(statement);
      }
      else {
        comments.addPending(child);
      }
    }

    if (statementList != null) {
      branches.add(new Branch(statementList, true, comments.fetchTexts()));
    }
    return branches;
  }

  static boolean areDuplicates(List<Branch> branches, int index, int otherIndex) {
    Branch branch = branches.get(index);
    Branch otherBranch = branches.get(otherIndex);
    if (branch.canFallThrough() || otherBranch.canFallThrough() ||
        branch.isSimpleExit() != otherBranch.isSimpleExit()) {
      return false;
    }

    Match match = branch.match(otherBranch);
    if (match != null) {
      Match otherMatch = otherBranch.match(branch);
      if (otherMatch != null) {
        if (branch.isSimpleExit() && otherBranch.isSimpleExit() && !Arrays.equals(branch.myCommentTexts, otherBranch.myCommentTexts)) {
          return false;
        }
        return ReturnValue.areEquivalent(match.getReturnValue(), otherMatch.getReturnValue());
      }
    }
    return false;
  }

  private static boolean hasImplicitBreak(@NotNull PsiStatement statement) {
    while (statement instanceof PsiSwitchLabelStatement) {
      statement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    }
    return statement == null || isBreakWithoutLabel(statement);
  }

  private static boolean isBreakWithoutLabel(@Nullable PsiStatement statement) {
    return statement instanceof PsiBreakStatement && ((PsiBreakStatement)statement).getLabelIdentifier() == null;
  }

  private static class Branch {
    private final PsiStatement[] myStatements;
    private final String[] myCommentTexts;
    private final boolean myIsSimpleExit;

    private DuplicatesFinder myFinder;
    private Boolean myCanFallThrough;

    Branch(@NotNull List<PsiStatement> statementList, boolean hasImplicitBreak, String[] commentTexts) {
      int lastIndex = statementList.size() - 1;
      PsiStatement lastStatement = statementList.get(lastIndex);
      if (hasImplicitBreak ||
          lastStatement instanceof PsiBreakStatement ||
          lastStatement instanceof PsiReturnStatement ||
          lastStatement instanceof PsiContinueStatement ||
          lastStatement instanceof PsiThrowStatement) {
        myCanFallThrough = false; // in more complex cases it will be computed lazily
      }
      myIsSimpleExit = lastIndex == 0 && isSimpleExit(lastStatement);
      if (lastIndex > 0 && isBreakWithoutLabel(lastStatement)) {
        statementList = statementList.subList(0, lastIndex); // trailing 'break' is already taken into account in myCanFallThrough
      }
      myStatements = statementList.toArray(PsiStatement.EMPTY_ARRAY);
      myCommentTexts = commentTexts;
    }

    @Nullable
    Match match(Branch other) {
      return getFinder().isDuplicate(other.myStatements[0], true);
    }

    boolean canFallThrough() {
      if (myCanFallThrough == null) {
        myCanFallThrough = calculateCanFallThrough(myStatements);
      }
      return myCanFallThrough;
    }

    boolean isSimpleExit() {
      return myIsSimpleExit;
    }

    @NotNull
    private DuplicatesFinder getFinder() {
      if (myFinder == null) {
        myFinder = createFinder(myStatements);
      }
      return myFinder;
    }

    @NotNull
    private static DuplicatesFinder createFinder(@NotNull PsiStatement[] statements) {
      Project project = statements[0].getProject();
      InputVariables noVariables = new InputVariables(Collections.emptyList(), project, new LocalSearchScope(statements), false);
      return new DuplicatesFinder(statements, noVariables, null, Collections.emptyList());
    }

    private static boolean calculateCanFallThrough(@NotNull PsiStatement[] statements) {
      PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(statements[0], PsiSwitchStatement.class);
      if (switchStatement != null) {
        PsiElement switchBody = switchStatement.getBody();
        if (switchBody != null) {
          try {
            ControlFlow flow = HighlightControlFlowUtil.getControlFlowNoConstantEvaluate(switchBody);
            int branchStart = flow.getStartOffset(statements[0]);
            int branchEnd = flow.getEndOffset(statements[statements.length - 1]);
            if (branchStart >= 0 && branchEnd >= 0) {
              return ControlFlowUtil.isInstructionReachable(flow, branchEnd, branchStart);
            }
          }
          catch (AnalysisCanceledException ignore) {
          }
        }
      }
      return true;
    }

    private static boolean isSimpleExit(@Nullable PsiStatement statement) {
      if (statement instanceof PsiBreakStatement ||
          statement instanceof PsiContinueStatement ||
          statement instanceof PsiThrowStatement) {
        return true;
      }
      if (statement instanceof PsiReturnStatement) {
        return isSimpleExpression(((PsiReturnStatement)statement).getReturnValue());
      }
      return false;
    }

    private static boolean isSimpleExpression(@Nullable PsiExpression expression) {
      expression = PsiUtil.deparenthesizeExpression(expression);
      if (expression == null || expression instanceof PsiLiteralExpression) {
        return true;
      }
      if (expression instanceof PsiReferenceExpression) {
        PsiExpression qualifier = ((PsiReferenceExpression)expression).getQualifierExpression();
        return qualifier == null || qualifier instanceof PsiQualifiedExpression;
      }
      if (expression instanceof PsiUnaryExpression) {
        return isSimpleExpression(((PsiUnaryExpression)expression).getOperand());
      }
      return false;
    }
  }

  private static class Comments {
    private final List<String> myTexts = new ArrayList<>();
    private final List<PsiElement> myPending = new ArrayList<>();

    String[] fetchTexts() {
      String[] result = ArrayUtil.toStringArray(myTexts);
      myTexts.clear();
      return result;
    }

    void addFrom(PsiStatement statement) {
      // The comments followed by a switch label are attached to that switch label.
      // They're pending until we know if the next statement is a label or not.
      for (PsiElement pending : myPending) {
        collectCommentTexts(pending, myTexts);
      }
      myPending.clear();
      collectCommentTexts(statement, myTexts);
    }

    public void addPending(PsiElement element) {
      myPending.add(element);
    }
  }
}
