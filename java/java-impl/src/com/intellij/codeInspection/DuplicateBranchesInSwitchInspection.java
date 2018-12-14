// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeElement;
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
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

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

      for (List<Branch> branches : collectProbablySimilarBranches(switchStatement)) {
        registerProblems(branches);
      }
    }

    void registerProblems(List<Branch> branches) {
      int size = branches.size();
      if (size > 1 && size <= 20) {
        boolean[] isDuplicate = new boolean[size];

        int defaultIndex = ContainerUtil.indexOf(branches, Branch::isDefault);
        if (defaultIndex >= 0) {
          Branch defaultBranch = branches.get(defaultIndex);
          for (int index = 0; index < size; index++) {
            if (index != defaultIndex) {
              Branch branch = branches.get(index);
              if (areDuplicates(defaultBranch, branch)) {
                isDuplicate[index] = isDuplicate[defaultIndex] = true;
                highlightDefaultDuplicate(branch.myStatements);
              }
            }
          }
        }

        for (int index = 0; index < size - 1; index++) {
          if (isDuplicate[index]) continue;
          Branch branch = branches.get(index);

          for (int otherIndex = index + 1; otherIndex < size; otherIndex++) {
            if (isDuplicate[otherIndex]) continue;
            Branch otherBranch = branches.get(otherIndex);

            if (areDuplicates(branch, otherBranch)) {
              isDuplicate[otherIndex] = true;
              highlightDuplicate(otherBranch.myStatements, branch.getSwitchLabelText());
            }
          }
        }
      }
    }

    private void highlightDuplicate(@NotNull PsiStatement[] statements, String switchLabelText) {
      ProblemDescriptor descriptor = InspectionManager.getInstance(myHolder.getProject())
        .createProblemDescriptor(statements[0], statements[statements.length - 1],
                                 InspectionsBundle.message("inspection.duplicate.branches.in.switch.message"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myHolder.isOnTheFly(),
                                 switchLabelText != null ? new MergeBranchesFix(switchLabelText) : null);
      myHolder.registerProblem(descriptor);
    }

    private void highlightDefaultDuplicate(PsiStatement[] statements) {
      ProblemDescriptor descriptor = InspectionManager.getInstance(myHolder.getProject())
        .createProblemDescriptor(statements[0], statements[statements.length - 1],
                                 InspectionsBundle.message("inspection.duplicate.branches.in.switch.redundant.message"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myHolder.isOnTheFly(),
                                 new DeleteRedundantBranchFix(), new MergeWithDefaultBranchFix());
      myHolder.registerProblem(descriptor);
    }
  }

  @NotNull
  static Collection<List<Branch>> collectProbablySimilarBranches(@NotNull PsiSwitchStatement switchStatement) {
    PsiCodeBlock body = switchStatement.getBody();
    if (body == null) return Collections.emptyList();

    List<PsiStatement> statementList = null;
    Comments comments = new Comments();

    Branch previousBranch = null;
    TIntObjectHashMap<List<Branch>> branchesByHash = new TIntObjectHashMap<>();
    for (PsiElement child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiSwitchLabelStatement) {
        PsiSwitchLabelStatement switchLabel = (PsiSwitchLabelStatement)child;
        previousBranch = addBranchToMap(branchesByHash, statementList, hasImplicitBreak(switchLabel), comments, previousBranch);

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

    addBranchToMap(branchesByHash, statementList, true, comments, previousBranch);

    Collection<List<Branch>> result = new ArrayList<>();
    branchesByHash.forEachValue(result::add); // mini-hack: ArrayList.add() always returns true
    return result;
  }

  @Nullable
  private static Branch addBranchToMap(@NotNull TIntObjectHashMap<List<Branch>> branchesByHash,
                                       @Nullable List<PsiStatement> statementList,
                                       boolean hasImplicitBreak,
                                       @NotNull Comments comments,
                                       @Nullable Branch previousBranch) {
    if (statementList == null || statementList.isEmpty()) {
      return previousBranch;
    }
    Branch branch = new Branch(statementList, hasImplicitBreak, comments.fetchTexts());
    if (previousBranch == null || !previousBranch.canFallThrough()) {
      int hash = branch.hash();
      List<Branch> branches = branchesByHash.get(hash);
      if (branches == null) branchesByHash.put(hash, branches = new ArrayList<>());
      branches.add(branch);
    }
    return branch;
  }

  static boolean areDuplicates(Branch branch, Branch otherBranch) {
    if (branch.isSimpleExit() != otherBranch.isSimpleExit() ||
        branch.canFallThrough() || otherBranch.canFallThrough() ||
        branch.length() != otherBranch.length()) {
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
    return statement instanceof PsiBreakStatement && ((PsiBreakStatement)statement).getLabelExpression() == null;
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
      FixContext context = new FixContext();
      if (context.prepare(descriptor.getStartElement(), branch -> mySwitchLabelText.equals(branch.getSwitchLabelText()))) {
        context.moveBranchLabel();
        context.deleteRedundantComments();
        context.deleteStatements();
      }
    }
  }

  private static class MergeWithDefaultBranchFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.duplicate.branches.in.switch.merge.with.default.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      FixContext context = new FixContext();
      if (context.prepare(descriptor.getStartElement(), Branch::isDefault)) {
        context.moveBranchLabel();
        context.deleteRedundantComments();
        context.deleteStatements();
      }
    }
  }

  private static class DeleteRedundantBranchFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.duplicate.branches.in.switch.redundant.fix.name");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.duplicate.branches.in.switch.redundant.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      FixContext context = new FixContext();
      if (context.prepare(descriptor.getStartElement(), Branch::isDefault)) {
        context.deleteBranchLabel();
        context.deleteStatements();
      }
    }
  }

  static class FixContext {
    private Branch myBranchToDelete;
    private Branch myBranchToMergeWith;
    private List<PsiElement> myBranchPrefixToMove;
    private PsiSwitchLabelStatement myLabelToMergeWith;
    private Set<String> myCommentsToMergeWith;
    private PsiElement myNextFromLabelToMergeWith;

    private boolean prepare(PsiElement startElement, Predicate<Branch> shouldMergeWith) {
      PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(startElement, PsiSwitchStatement.class);
      if (switchStatement == null) return false;

      List<Branch> candidateBranches = null;
      for (List<Branch> branches : collectProbablySimilarBranches(switchStatement)) {
        myBranchToDelete = ContainerUtil.find(branches, branch -> branch.myStatements[0] == startElement);
        if (myBranchToDelete != null) {
          candidateBranches = branches;
          break;
        }
      }
      if (myBranchToDelete == null || candidateBranches == null) return false;

      for (Branch branch : candidateBranches) {
        if (shouldMergeWith.test(branch) && areDuplicates(myBranchToDelete, branch)) {
          myBranchToMergeWith = branch;
          break;
        }
      }
      if (myBranchToMergeWith == null) return false;

      myBranchPrefixToMove = myBranchToDelete.getBranchPrefix();
      if (myBranchPrefixToMove.isEmpty()) return false;

      myLabelToMergeWith = PsiTreeUtil.getPrevSiblingOfType(myBranchToMergeWith.myStatements[0], PsiSwitchLabelStatement.class);
      if (myLabelToMergeWith == null) return false;

      myNextFromLabelToMergeWith = PsiTreeUtil.skipWhitespacesForward(myLabelToMergeWith);

      myCommentsToMergeWith = ContainerUtil.set(myBranchToMergeWith.myCommentTexts);
      return true;
    }

    void moveBranchLabel() {
      PsiElement firstElementToMove = myBranchPrefixToMove.get(0);
      PsiElement lastElementToMove = myBranchPrefixToMove.get(myBranchPrefixToMove.size() - 1);

      PsiElement moveTarget = myLabelToMergeWith;
      if (myLabelToMergeWith.isDefaultCase()) {
        PsiElement prevElement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(myLabelToMergeWith);
        if (prevElement != null) moveTarget = prevElement;
      }
      moveTarget.getParent().addRangeAfter(firstElementToMove, lastElementToMove, moveTarget);
      firstElementToMove.getParent().deleteChildRange(firstElementToMove, lastElementToMove);
    }

    void deleteStatements() {
      CommentTracker tracker = new CommentTracker();
      PsiStatement[] statementsToDelete = myBranchToDelete.getStatementsToDelete();
      for (PsiStatement statement : statementsToDelete) {
        PsiTreeUtil.processElements(statement, child -> {
          if (isRedundantComment(myCommentsToMergeWith, child)) {
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

    void deleteRedundantComments() {
      List<PsiElement> redundantComments = new ArrayList<>();
      for (PsiElement element = myLabelToMergeWith.getNextSibling();
           element != null && element != myNextFromLabelToMergeWith;
           element = element.getNextSibling()) {
        PsiTreeUtil.processElements(element, child -> {
          if (isRedundantComment(myCommentsToMergeWith, child)) {
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

    void deleteBranchLabel() {
      List<PsiElement> toDelete = new ArrayList<>();
      CommentTracker tracker = new CommentTracker();

      for (PsiElement element : myBranchPrefixToMove) {
        if (element instanceof PsiWhiteSpace) {
          continue;
        }
        toDelete.add(element);
        PsiTreeUtil.processElements(element, child -> {
          if (isRedundantComment(myCommentsToMergeWith, child)) {
            tracker.markUnchanged(child);
          }
          return true;
        });
      }
      int size = toDelete.size();
      if (size != 0) {
        for (int i = 0; i < size - 1; i++) {
          tracker.delete(toDelete.get(i));
        }
        tracker.deleteAndRestoreComments(toDelete.get(size - 1));
      }
    }
  }

  private static class Branch {
    private final PsiStatement[] myStatements;
    private final String[] myCommentTexts;
    private final boolean myIsDefault;
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
      myIsDefault = calculateIsDefault(statementList.get(0));
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

    int hash() {
      int hash = myStatements.length;
      // Don't want to hash the whole PSI tree in-depth because it's rather slow and is rarely needed.
      for (PsiStatement statement : myStatements) {
        if (statement instanceof TreeElement) { // all known PSI statements are tree elements
          short index = ((TreeElement)statement).getElementType().getIndex();
          hash = hash * 31 + index;
        }
      }
      return hash;
    }

    boolean isDefault() {
      return myIsDefault;
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
        PsiExpressionList caseValues = switchLabel.getCaseValues();
        if (caseValues != null) {
          PsiExpression[] expressions = caseValues.getExpressions();
          if (expressions.length != 0) {
            return PsiKeyword.CASE + ' ' + expressions[0].getText();
          }
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
           element != null && !isLeftBrace(element) && (element instanceof PsiSwitchLabelStatement || !(element instanceof PsiStatement));
           element = element.getPrevSibling()) {
        result.add(element);
      }
      Collections.reverse(result);
      return result;
    }

    private static boolean isLeftBrace(PsiElement element) {
      return element instanceof PsiJavaToken && JavaTokenType.LBRACE.equals(((PsiJavaToken)element).getTokenType());
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

    private static boolean calculateIsDefault(PsiStatement statement) {
      for (PsiElement element = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
           element instanceof PsiSwitchLabelStatement;
           element = PsiTreeUtil.getPrevSiblingOfType(element, PsiStatement.class)) {
        if (((PsiSwitchLabelStatement)element).isDefaultCase()) {
          return true;
        }
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
