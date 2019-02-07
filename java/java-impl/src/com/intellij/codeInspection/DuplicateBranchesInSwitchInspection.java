// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.refactoring.util.duplicates.ReturnValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
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

      if (isEnhancedSwitch(switchStatement)) {
        visitEnhancedSwitch(switchStatement);
        return;
      }

      for (List<Branch> branches : collectProbablySimilarBranches(switchStatement)) {
        registerProblems(branches);
      }
    }

    @Override
    public void visitSwitchExpression(PsiSwitchExpression switchExpression) {
      super.visitSwitchExpression(switchExpression);

      visitEnhancedSwitch(switchExpression);
    }

    private void visitEnhancedSwitch(@NotNull PsiSwitchBlock switchBlock) {
      Collection<List<Rule>> probablySimilarRules = collectProbablySimilarRules(switchBlock);
      for (List<Rule> rules : probablySimilarRules) {
        registerProblems(rules);
      }
    }

    void registerProblems(List<? extends BranchBase> branches) {
      int size = branches.size();
      if (size > 1) {
        boolean[] isDuplicate = new boolean[size];

        int defaultIndex = ContainerUtil.indexOf(branches, BranchBase::isDefault);
        if (defaultIndex >= 0) {
          BranchBase defaultBranch = branches.get(defaultIndex);
          for (int index = 0; index < size; index++) {
            if (index != defaultIndex) {
              BranchBase branch = branches.get(index);
              if (areDuplicates(defaultBranch, branch)) {
                isDuplicate[index] = isDuplicate[defaultIndex] = true;
                highlightDefaultDuplicate(branch);
              }
            }
          }
        }

        int compareCount = 0;
        for (int index = 0; index < size - 1; index++) {
          if (isDuplicate[index]) continue;
          BranchBase branch = branches.get(index);

          for (int otherIndex = index + 1; otherIndex < size; otherIndex++) {
            if (isDuplicate[otherIndex]) continue;
            if (++compareCount > 200) return; // avoid quadratic loop over too large list, but at least try to do something in that case
            BranchBase otherBranch = branches.get(otherIndex);

            if (areDuplicates(branch, otherBranch)) {
              isDuplicate[otherIndex] = true;
              highlightDuplicate(otherBranch, branch);
            }
          }
        }
      }
    }

    private void highlightDuplicate(@NotNull BranchBase duplicate, @NotNull BranchBase original) {
      ProblemDescriptor descriptor = InspectionManager.getInstance(myHolder.getProject())
        .createProblemDescriptor(duplicate.getFirstStatement(), duplicate.getLastStatement(),
                                 duplicate.getCaseBranchMessage(),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myHolder.isOnTheFly(),
                                 original.newMergeCasesFix());
      myHolder.registerProblem(descriptor);
    }

    private void highlightDefaultDuplicate(BranchBase branch) {
      ProblemDescriptor descriptor = InspectionManager.getInstance(myHolder.getProject())
        .createProblemDescriptor(branch.getFirstStatement(), branch.getLastStatement(),
                                 branch.getDefaultBranchMessage(),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myHolder.isOnTheFly(),
                                 branch.newDeleteCaseFix(), branch.newMergeWithDefaultFix());
      myHolder.registerProblem(descriptor);
    }

    private boolean isEnhancedSwitch(@NotNull PsiSwitchStatement switchStatement) {
      PsiFile file = myHolder.getFile();
      if (file instanceof PsiJavaFile && ((PsiJavaFile)file).getLanguageLevel().isAtLeast(LanguageLevel.JDK_12_PREVIEW)) {
        PsiCodeBlock body = switchStatement.getBody();
        if (body != null) {
          for (PsiElement element = body.getFirstChild(); element != null; element = element.getNextSibling()) {
            if (element instanceof PsiSwitchLabeledRuleStatement) {
              return true;
            }
            if (element instanceof PsiSwitchLabelStatement) {
              return false;
            }
          }
        }
      }
      return false;
    }
  }

  @NotNull
  private static Collection<List<Rule>> collectProbablySimilarRules(@NotNull PsiSwitchBlock switchBlock) {
    PsiCodeBlock switchBody = switchBlock.getBody();
    if (switchBody == null) {
      return Collections.emptyList();
    }

    TIntObjectHashMap<List<Rule>> rulesByHash = new TIntObjectHashMap<>();
    List<String> commentTexts = new ArrayList<>();
    for (PsiElement element = switchBody.getFirstChild(); element != null; element = element.getNextSibling()) {
      if (!(element instanceof PsiSwitchLabeledRuleStatement)) {
        collectCommentTexts(element, commentTexts);
        continue;
      }
      PsiSwitchLabeledRuleStatement ruleStatement = (PsiSwitchLabeledRuleStatement)element;
      PsiStatement body = ruleStatement.getBody();
      if (body != null) {
        collectCommentTexts(ruleStatement, commentTexts);
        Rule rule = new Rule(ruleStatement, body, ArrayUtil.toStringArray(commentTexts));
        commentTexts.clear();
        int hash = rule.hash();
        List<Rule> list = rulesByHash.get(hash);
        if (list == null) rulesByHash.put(hash, list = new ArrayList<>());
        list.add(rule);
      }
    }

    Collection<List<Rule>> result = new ArrayList<>();
    rulesByHash.forEachValue(result::add); // mini-hack: ArrayList.add() always returns true
    return result;
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

  static boolean areDuplicates(@NotNull BranchBase branch, @NotNull BranchBase otherBranch) {
    if (branch.isSimpleExit() != otherBranch.isSimpleExit() ||
        branch.canFallThrough() != otherBranch.canFallThrough() ||
        branch.length() != otherBranch.length()) {
      return false;
    }

    Match match = branch.match(otherBranch);
    if (match != null) {
      Match otherMatch = otherBranch.match(branch);
      if (otherMatch != null) {
        if (branch.isSimpleExit() &&
            otherBranch.isSimpleExit() &&
            !Arrays.equals(branch.getCommentTexts(), otherBranch.getCommentTexts())) {
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

  @Contract("_,null -> false")
  private static boolean isRedundantComment(@NotNull Set<String> existingComments, @Nullable PsiElement element) {
    if (element instanceof PsiComment) {
      String text = getCommentText((PsiComment)element);
      return text.isEmpty() || existingComments.contains(text);
    }
    return false;
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
      BranchFixContext context = new BranchFixContext();
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
      BranchFixContext context = new BranchFixContext();
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
      BranchFixContext context = new BranchFixContext();
      if (context.prepare(descriptor.getStartElement(), Branch::isDefault)) {
        context.deleteBranchLabel();
        context.deleteStatements();
      }
    }
  }

  static class BranchFixContext {
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

      myCommentsToMergeWith = ContainerUtil.set(myBranchToMergeWith.getCommentTexts());
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

  private static abstract class BranchBase {
    private final String[] myCommentTexts;
    private DuplicatesFinder myFinder;

    BranchBase(@NotNull String[] commentTexts) {
      myCommentTexts = commentTexts;
    }

    abstract boolean isDefault();

    @NotNull
    abstract PsiStatement[] getStatements();

    @Nullable
    abstract String getSwitchLabelText();

    abstract boolean isSimpleExit();

    abstract boolean canFallThrough();

    abstract int length();

    String[] getCommentTexts() {
      return myCommentTexts;
    }

    abstract PsiStatement getFirstStatement();

    abstract PsiStatement getLastStatement();

    @Nullable
    abstract LocalQuickFix newMergeCasesFix();

    abstract LocalQuickFix newDeleteCaseFix();

    abstract LocalQuickFix newMergeWithDefaultFix();

    @Nullable
    Match match(BranchBase other) {
      return getFinder().isDuplicate(other.getFirstStatement(), true);
    }

    @NotNull
    private DuplicatesFinder getFinder() {
      if (myFinder == null) {
        myFinder = createFinder(getStatements());
      }
      return myFinder;
    }

    String getCaseBranchMessage() {
      return InspectionsBundle.message("inspection.duplicate.branches.in.switch.statement.message");
    }

    String getDefaultBranchMessage() {
      return InspectionsBundle.message("inspection.duplicate.branches.in.switch.statement.default.message");
    }

    @Override
    public String toString() {
      return StringUtil.notNullize(getSwitchLabelText());
    }

    @NotNull
    private static DuplicatesFinder createFinder(@NotNull PsiStatement[] statements) {
      Project project = statements[0].getProject();
      InputVariables noVariables = new InputVariables(Collections.emptyList(), project, new LocalSearchScope(statements), false);
      return new DuplicatesFinder(statements, noVariables, null, Collections.emptyList());
    }

    @Nullable
    static String getSwitchLabelText(@Nullable PsiSwitchLabelStatementBase switchLabel) {
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

    static int hashElement(@NotNull PsiElement element, int depth) {
      if (element instanceof PsiExpression) {
        return hashExpression((PsiExpression)element);
      }
      IElementType type = PsiUtilCore.getElementType(element);
      int hash = type != null ? type.hashCode() : 0;
      if (depth > 0) {
        int count = 0;
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
          if (child instanceof PsiWhiteSpace || child instanceof PsiComment ||
              child instanceof PsiJavaToken) { // significant tokens are taken into account by getElementType()
            continue;
          }
          hash = hash * 31 + hashElement(child, depth - 1);
          count++;
        }
        if (count != 0) {
          hash = hash * 31 + count;
        }
      }
      return hash;
    }

    static int hashExpression(@Nullable PsiExpression expression) {
      if (expression == null) {
        return 0;
      }
      if (expression instanceof PsiParenthesizedExpression) {
        return hashExpression(((PsiParenthesizedExpression)expression).getExpression());
      }

      short index = expression.getNode().getElementType().getIndex();
      if (expression instanceof PsiReferenceExpression) {
        return hashReference((PsiReferenceExpression)expression, index);
      }
      if (expression instanceof PsiMethodCallExpression) {
        return hashReference(((PsiMethodCallExpression)expression).getMethodExpression(), index);
      }
      if (expression instanceof PsiNewExpression) {
        PsiJavaCodeReferenceElement reference = ((PsiNewExpression)expression).getClassOrAnonymousClassReference();
        if (reference != null) {
          return hashReference(reference, index);
        }
      }
      if (expression instanceof PsiAssignmentExpression) {
        PsiExpression lExpression = ((PsiAssignmentExpression)expression).getLExpression();
        PsiExpression rExpression = ((PsiAssignmentExpression)expression).getRExpression();
        return ((hashExpression(lExpression) * 31) + hashExpression(rExpression)) * 31 + index;
      }
      return index;
    }

    static int hashReference(@NotNull PsiJavaCodeReferenceElement reference, short index) {
      return Objects.hashCode(reference.getReferenceName()) * 31 + index;
    }

    static int hashStatements(@NotNull PsiStatement[] statements) {
      int hash = statements.length;
      for (PsiStatement statement : statements) {
        hash = hash * 31 + hashElement(statement, 2); // Don't want to hash the whole PSI tree because it might be quite slow
      }
      return hash;
    }
  }

  private static class Branch extends BranchBase {
    private final PsiStatement[] myStatements;
    private final boolean myIsDefault;
    private final boolean myIsSimpleExit;
    private final boolean myCanFallThrough;

    Branch(@NotNull List<PsiStatement> statementList, boolean hasImplicitBreak, @NotNull String[] commentTexts) {
      super(commentTexts);
      int lastIndex = statementList.size() - 1;
      PsiStatement lastStatement = statementList.get(lastIndex);
      myCanFallThrough = !hasImplicitBreak && ControlFlowUtils.statementMayCompleteNormally(lastStatement);
      myIsSimpleExit = lastIndex == 0 && isSimpleExit(lastStatement);
      if (lastIndex > 0 && isBreakWithoutLabel(lastStatement)) {
        statementList = statementList.subList(0, lastIndex); // trailing 'break' is already taken into account in myCanFallThrough
      }
      myStatements = statementList.toArray(PsiStatement.EMPTY_ARRAY);
      myIsDefault = calculateIsDefault(statementList.get(0));
    }

    @Override
    boolean canFallThrough() {
      return myCanFallThrough;
    }

    @Override
    boolean isSimpleExit() {
      return myIsSimpleExit;
    }

    @Override
    int length() {
      return myStatements.length;
    }

    int hash() {
      return hashStatements(myStatements);
    }

    @Override
    boolean isDefault() {
      return myIsDefault;
    }

    @Nullable
    @Override
    String getSwitchLabelText() {
      PsiSwitchLabelStatement switchLabel = null;
      for (PsiStatement statement = PsiTreeUtil.getPrevSiblingOfType(myStatements[0], PsiStatement.class);
           statement instanceof PsiSwitchLabelStatement;
           statement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class)) {
        switchLabel = (PsiSwitchLabelStatement)statement;
      }

      return getSwitchLabelText(switchLabel);
    }

    @NotNull
    @Override
    PsiStatement[] getStatements() {
      return myStatements;
    }

    @Override
    PsiStatement getFirstStatement() {
      return myStatements[0];
    }

    @Override
    PsiStatement getLastStatement() {
      return myStatements[myStatements.length - 1];
    }

    @Nullable
    @Override
    LocalQuickFix newMergeCasesFix() {
      String switchLabelText = getSwitchLabelText();
      return switchLabelText != null ? new MergeBranchesFix(switchLabelText) : null;
    }

    @Override
    LocalQuickFix newMergeWithDefaultFix() {
      return new MergeWithDefaultBranchFix();
    }

    @Override
    LocalQuickFix newDeleteCaseFix() {
      return new DeleteRedundantBranchFix();
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

  private static class Rule extends BranchBase {
    private final PsiSwitchLabeledRuleStatement myRule;
    private final PsiStatement myBody;

    Rule(@NotNull PsiSwitchLabeledRuleStatement rule, @NotNull PsiStatement body, @NotNull String[] commentTexts) {
      super(commentTexts);
      myRule = rule;
      myBody = body;
    }

    int hash() {
      PsiStatement body = myRule.getBody();
      if (body instanceof PsiExpressionStatement) {
        return hashExpression(((PsiExpressionStatement)body).getExpression()) * 31 + JavaElementType.EXPRESSION_STATEMENT.getIndex();
      }
      if (body instanceof PsiThrowStatement) {
        return hashExpression(((PsiThrowStatement)body).getException()) * 31 + JavaElementType.THROW_STATEMENT.getIndex();
      }
      if (body instanceof PsiBlockStatement) {
        PsiCodeBlock block = ((PsiBlockStatement)body).getCodeBlock();
        return hashStatements(block.getStatements()) * 31 + JavaElementType.BLOCK_STATEMENT.getIndex();
      }
      return 0;
    }

    @Override
    boolean isDefault() {
      return myRule.isDefaultCase();
    }

    @Override
    boolean isSimpleExit() {
      return myBody instanceof PsiExpressionStatement || myBody instanceof PsiThrowStatement;
    }

    @Override
    boolean canFallThrough() {
      return false;
    }

    @Override
    int length() {
      return myBody instanceof PsiBlockStatement ? ((PsiBlockStatement)myBody).getCodeBlock().getStatementCount() : 1;
    }

    @NotNull
    @Override
    PsiStatement[] getStatements() {
      return new PsiStatement[]{myBody};
    }

    @Override
    PsiStatement getFirstStatement() {
      return myBody;
    }

    @Override
    PsiStatement getLastStatement() {
      return myBody;
    }

    @Nullable
    @Override
    String getSwitchLabelText() {
      return getSwitchLabelText(myRule);
    }

    @Override
    String getCaseBranchMessage() {
      if (myRule.getEnclosingSwitchBlock() instanceof PsiSwitchExpression) {
        return myBody instanceof PsiExpressionStatement
               ? InspectionsBundle.message("inspection.duplicate.branches.in.switch.result.message")
               : InspectionsBundle.message("inspection.duplicate.branches.in.switch.expression.message");
      }
      return super.getCaseBranchMessage();
    }

    @Override
    String getDefaultBranchMessage() {
      if (myRule.getEnclosingSwitchBlock() instanceof PsiSwitchExpression) {
        return myBody instanceof PsiExpressionStatement
               ? InspectionsBundle.message("inspection.duplicate.branches.in.switch.default.result.message")
               : InspectionsBundle.message("inspection.duplicate.branches.in.switch.expression.default.message");
      }
      return super.getDefaultBranchMessage();
    }

    @Nullable
    @Override
    LocalQuickFix newMergeCasesFix() {
      String switchLabelText = getSwitchLabelText();
      return switchLabelText != null ? new MergeRulesFix(switchLabelText, isResultExpression()) : null;
    }

    @Override
    LocalQuickFix newMergeWithDefaultFix() {
      return null;
    }

    @Override
    LocalQuickFix newDeleteCaseFix() {
      return new DeleteRedundantRuleFix(isResultExpression());
    }

    private boolean isResultExpression() {
      return myRule.getEnclosingSwitchBlock() instanceof PsiSwitchExpression && myBody instanceof PsiExpressionStatement;
    }
  }

  private static class MergeRulesFix implements LocalQuickFix {
    @NotNull private final String mySwitchLabelText;
    private final boolean myIsResultExpression;

    MergeRulesFix(@NotNull String switchLabelText, boolean isResultExpression) {
      mySwitchLabelText = switchLabelText;
      myIsResultExpression = isResultExpression;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return myIsResultExpression
             ? InspectionsBundle.message("inspection.duplicate.branches.in.switch.expression.fix.family.name")
             : InspectionsBundle.message("inspection.duplicate.branches.in.switch.fix.family.name");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.duplicate.branches.in.switch.fix.name", mySwitchLabelText);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      RuleFixContext context = new RuleFixContext();
      if (context.prepare(descriptor.getStartElement(), rule -> mySwitchLabelText.equals(rule.getSwitchLabelText()))) {
        context.copyCaseValues();
        context.deleteRule();
      }
    }
  }

  private static class DeleteRedundantRuleFix implements LocalQuickFix {
    private final boolean myIsResultExpression;

    DeleteRedundantRuleFix(boolean isResultExpression) {
      myIsResultExpression = isResultExpression;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return myIsResultExpression
             ? InspectionsBundle.message("inspection.duplicate.branches.in.switch.redundant.expression.fix.name")
             : InspectionsBundle.message("inspection.duplicate.branches.in.switch.redundant.fix.name");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return myIsResultExpression
             ? InspectionsBundle.message("inspection.duplicate.branches.in.switch.redundant.expression.fix.family.name")
             : InspectionsBundle.message("inspection.duplicate.branches.in.switch.redundant.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      RuleFixContext context = new RuleFixContext();
      if (context.prepare(descriptor.getStartElement(), Rule::isDefault)) {
        context.deleteRule();
      }
    }
  }

  private static class RuleFixContext {
    private Rule myRuleToDelete;
    private Rule myRuleToMergeWith;
    private Set<String> myCommentsToMergeWith;

    boolean prepare(PsiElement startElement, Predicate<Rule> shouldMergeWith) {
      if (startElement != null) {
        PsiSwitchLabeledRuleStatement ruleStatement = ObjectUtils.tryCast(startElement.getParent(), PsiSwitchLabeledRuleStatement.class);
        if (ruleStatement != null) {
          PsiSwitchBlock switchBlock = ruleStatement.getEnclosingSwitchBlock();
          if (switchBlock != null) {
            List<Rule> candidateRules = null;
            for (List<Rule> rules : collectProbablySimilarRules(switchBlock)) {
              myRuleToDelete = ContainerUtil.find(rules, r -> r.myRule == ruleStatement);
              if (myRuleToDelete != null) {
                candidateRules = rules;
                break;
              }
            }
            if (candidateRules == null) {
              return false;
            }
            for (Rule rule : candidateRules) {
              if (shouldMergeWith.test(rule)) {
                myRuleToMergeWith = rule;
                break;
              }
            }
            if (myRuleToMergeWith == null) {
              return false;
            }
          }
        }
      }
      myCommentsToMergeWith = ContainerUtil.set(myRuleToMergeWith.getCommentTexts());
      return true;
    }

    void copyCaseValues() {
      PsiExpressionList caseValuesToMergeWith = myRuleToMergeWith.myRule.getCaseValues();
      if (myRuleToDelete.myRule.getCaseValues() != null && caseValuesToMergeWith != null) {
        for (PsiExpression caseValue : myRuleToDelete.myRule.getCaseValues().getExpressions()) {
          caseValuesToMergeWith.addAfter(caseValue, caseValuesToMergeWith.getLastChild());
        }
      }
    }

    void deleteRule() {
      CommentTracker tracker = new CommentTracker();
      PsiTreeUtil.processElements(myRuleToDelete.myRule, child -> {
        if (isRedundantComment(myCommentsToMergeWith, child)) {
          tracker.markUnchanged(child);
        }
        return true;
      });

      tracker.deleteAndRestoreComments(myRuleToDelete.myRule);
    }
  }
}
