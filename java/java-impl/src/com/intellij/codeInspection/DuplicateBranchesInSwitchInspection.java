// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.refactoring.util.duplicates.ReturnValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.siyeh.ig.migration.TryWithIdenticalCatchesInspection.collectCommentTexts;
import static com.siyeh.ig.migration.TryWithIdenticalCatchesInspection.getCommentText;

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

      for (List<Branch> branches : collectSameLengthBranches(switchStatement)) {
        registerProblems(branches);
      }
    }

    void registerProblems(List<Branch> branches) {
      int size = branches.size();
      if (size > 1) {
        boolean[] isDuplicate = new boolean[size];
        for (int index = 0; index < size - 1; index++) {
          if (isDuplicate[index]) continue;

          for (int otherIndex = index + 1; otherIndex < size; otherIndex++) {
            Branch branch = branches.get(index);
            Branch otherBranch = branches.get(otherIndex);

            if (areDuplicates(branch, otherBranch)) {
              isDuplicate[otherIndex] = true;
              registerProblem(otherBranch.myStatements, branch.getSwitchLabelText());

              if (!isDuplicate[index]) {
                isDuplicate[index] = true;
                registerProblem(branch.myStatements, null);
              }
            }
          }
        }
      }
    }

    private void registerProblem(@NotNull PsiStatement[] statements, String switchLabelText) {
      ProblemDescriptor descriptor = InspectionManager.getInstance(myHolder.getProject())
        .createProblemDescriptor(statements[0], statements[statements.length - 1],
                                 InspectionsBundle.message("inspection.duplicate.branches.in.switch.message"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myHolder.isOnTheFly(),
                                 switchLabelText != null ? new MergeBranchesFix(switchLabelText) : null);
      myHolder.registerProblem(descriptor);
    }
  }

  @NotNull
  static Collection<List<Branch>> collectSameLengthBranches(@NotNull PsiSwitchStatement switchStatement) {
    PsiCodeBlock body = switchStatement.getBody();
    if (body == null) return Collections.emptyList();

    List<PsiStatement> statementList = null;
    Comments comments = new Comments();

    Branch previousBranch = null;
    Map<Integer, List<Branch>> branchesByLength = new HashMap<>();
    for (PsiElement child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiSwitchLabelStatement) {
        PsiSwitchLabelStatement switchLabel = (PsiSwitchLabelStatement)child;
        previousBranch = addBranchToMap(branchesByLength, statementList, hasImplicitBreak(switchLabel), comments, previousBranch);

        statementList = null;
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

    addBranchToMap(branchesByLength, statementList, true, comments, previousBranch);
    return branchesByLength.values();
  }

  @Nullable
  private static Branch addBranchToMap(@NotNull Map<Integer, List<Branch>> branchesByLength,
                                       @Nullable List<PsiStatement> statementList,
                                       boolean hasImplicitBreak,
                                       @NotNull Comments comments,
                                       @Nullable Branch previousBranch) {
    if (statementList == null || statementList.isEmpty()) {
      return previousBranch;
    }
    Branch branch = new Branch(statementList, hasImplicitBreak, comments.fetchTexts());
    if (previousBranch == null || !previousBranch.canFallThrough()) {
      List<Branch> branches = branchesByLength.computeIfAbsent(branch.length(), unused -> new ArrayList<>());
      branches.add(branch);
    }
    return branch;
  }

  static boolean areDuplicates(Branch branch, Branch otherBranch) {
    if (branch.isSimpleExit() != otherBranch.isSimpleExit() ||
        branch.canFallThrough() || otherBranch.canFallThrough()) {
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

  @Contract("null -> false")
  private static boolean isBreakWithoutLabel(@Nullable PsiStatement statement) {
    return statement instanceof PsiBreakStatement && ((PsiBreakStatement)statement).getLabelIdentifier() == null;
  }

  private static class MergeBranchesFix implements LocalQuickFix {
    @NotNull private final String mySwitchLabelText;

    MergeBranchesFix(@NotNull String switchLabelText) {
      mySwitchLabelText = switchLabelText;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.duplicate.branches.in.switch.fix.family.name");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.duplicate.branches.in.switch.fix.name", mySwitchLabelText);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement startElement = descriptor.getStartElement();
      PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(startElement, PsiSwitchStatement.class);
      if (switchStatement == null) return;

      Branch branchToDelete = null;
      List<Branch> candidateBranches = null;
      for (List<Branch> branches : collectSameLengthBranches(switchStatement)) {
        branchToDelete = ContainerUtil.find(branches, branch -> branch.myStatements[0] == startElement);
        if (branchToDelete != null) {
          candidateBranches = branches;
          break;
        }
      }

      if (branchToDelete == null) return;

      Branch branchToMergeWith = null;
      for (Branch branch : candidateBranches) {
        if (mySwitchLabelText.equals(branch.getSwitchLabelText()) && areDuplicates(branchToDelete, branch)) {
          branchToMergeWith = branch;
          break;
        }
      }
      if (branchToMergeWith == null) return;

      List<PsiElement> branchPrefixToMove = branchToDelete.getBranchPrefix();
      if (branchPrefixToMove.isEmpty()) return;

      PsiSwitchLabelStatement labelToMergeWith =
        PsiTreeUtil.getPrevSiblingOfType(branchToMergeWith.myStatements[0], PsiSwitchLabelStatement.class);
      if (labelToMergeWith == null) return;

      PsiElement oldNextElement = PsiTreeUtil.skipWhitespacesForward(labelToMergeWith);

      PsiElement firstElementToMove = branchPrefixToMove.get(0);
      PsiElement lastElementToMove = branchPrefixToMove.get(branchPrefixToMove.size() - 1);
      labelToMergeWith.getParent().addRangeAfter(firstElementToMove, lastElementToMove, labelToMergeWith);
      firstElementToMove.getParent().deleteChildRange(firstElementToMove, lastElementToMove);

      Set<String> commentsToMergeWith = ContainerUtil.set(branchToMergeWith.myCommentTexts);
      deleteRedundantComments(labelToMergeWith.getNextSibling(), oldNextElement, commentsToMergeWith);

      CommentTracker tracker = new CommentTracker();
      PsiStatement[] statementsToDelete = branchToDelete.getStatementsToDelete();
      for (PsiStatement statement : statementsToDelete) {
        PsiTreeUtil.processElements(statement, child -> {
          if (isRedundantComment(commentsToMergeWith, child)) {
            tracker.markUnchanged(child);
          }
          return true;
        });
      }
      for (int i = 0; i < statementsToDelete.length - 1; i++) {
        tracker.delete(statementsToDelete[i]);
      }
      tracker.deleteAndRestoreComments(statementsToDelete[statementsToDelete.length - 1]);
    }

    private static void deleteRedundantComments(@Nullable PsiElement startElement,
                                                @Nullable PsiElement stopElement,
                                                @NotNull Set<String> existingComments) {
      List<PsiElement> redundantComments = new ArrayList<>();
      for (PsiElement element = startElement; element != null && element != stopElement; element = element.getNextSibling()) {
        PsiTreeUtil.processElements(element, child -> {
          if (isRedundantComment(existingComments, child)) {
            redundantComments.add(child);
          }
          return true;
        });
      }
      redundantComments.forEach(PsiElement::delete);
    }

    @Contract("_,null -> false")
    private static boolean isRedundantComment(@NotNull Set<String> existingComments, @Nullable PsiElement element) {
      if (element instanceof PsiComment) {
        String text = getCommentText((PsiComment)element);
        return text.isEmpty() || existingComments.contains(text);
      }
      return false;
    }
  }

  private static class Branch {
    private final PsiStatement[] myStatements;
    private final String[] myCommentTexts;
    private final boolean myIsSimpleExit;
    private final boolean myCanFallThrough;

    private DuplicatesFinder myFinder;

    Branch(@NotNull List<PsiStatement> statementList, boolean hasImplicitBreak, @NotNull String[] commentTexts) {
      int lastIndex = statementList.size() - 1;
      PsiStatement lastStatement = statementList.get(lastIndex);
      myCanFallThrough = !hasImplicitBreak && ControlFlowUtils.statementMayCompleteNormally(lastStatement);
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
      return myCanFallThrough;
    }

    boolean isSimpleExit() {
      return myIsSimpleExit;
    }

    int length() {
      return myStatements.length;
    }

    @Nullable
    String getSwitchLabelText() {
      PsiSwitchLabelStatement switchLabel = null;
      for (PsiStatement statement = PsiTreeUtil.getPrevSiblingOfType(myStatements[0], PsiStatement.class);
           statement instanceof PsiSwitchLabelStatement;
           statement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class)) {
        switchLabel = (PsiSwitchLabelStatement)statement;
      }

      if (switchLabel != null) {
        if (switchLabel.isDefaultCase()) {
          return PsiKeyword.DEFAULT;
        }
        PsiExpression value = switchLabel.getCaseValue();
        if (value != null) {
          return PsiKeyword.CASE + ' ' + value.getText();
        }
      }
      return null;
    }

    /**
     * switch labels with comments and spaces
     */
    @NotNull
    List<PsiElement> getBranchPrefix() {
      List<PsiElement> result = new ArrayList<>();
      for (PsiElement element = myStatements[0].getPrevSibling();
           element != null && (element instanceof PsiSwitchLabelStatement || !(element instanceof PsiStatement));
           element = element.getPrevSibling()) {
        result.add(element);
      }
      Collections.reverse(result);
      return result;
    }

    PsiStatement[] getStatementsToDelete() {
      PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(myStatements[myStatements.length - 1], PsiStatement.class);
      if (isBreakWithoutLabel(nextStatement)) {
        PsiStatement[] statements = Arrays.copyOf(myStatements, myStatements.length + 1);
        statements[myStatements.length] = nextStatement; // it's the trailing 'break'
        return statements;
      }
      return myStatements;
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
