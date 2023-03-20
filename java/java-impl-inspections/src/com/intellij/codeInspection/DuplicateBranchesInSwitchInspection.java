// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.refactoring.util.duplicates.ReturnValue;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.migration.TryWithIdenticalCatchesInspection;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public final class DuplicateBranchesInSwitchInspection extends LocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(DuplicateBranchesInSwitchInspection.class);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new DuplicateBranchesVisitor(holder);
  }

  private static class DuplicateBranchesVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    DuplicateBranchesVisitor(ProblemsHolder holder) {myHolder = holder;}

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement switchStatement) {
      super.visitSwitchStatement(switchStatement);

      visitSwitchBlock(switchStatement);
    }

    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression switchExpression) {
      super.visitSwitchExpression(switchExpression);

      visitSwitchBlock(switchExpression);
    }

    private void visitSwitchBlock(PsiSwitchBlock switchBlock) {
      if (SwitchUtils.isRuleFormatSwitch(switchBlock)) {
        visitEnhancedSwitch(switchBlock);
        return;
      }

      for (List<Branch> branches : collectProbablySimilarBranches(switchBlock)) {
        registerProblems(branches);
      }
    }

    private void visitEnhancedSwitch(@NotNull PsiSwitchBlock switchBlock) {
      Collection<List<Rule>> probablySimilarRules = collectProbablySimilarRules(switchBlock);
      for (List<Rule> rules : probablySimilarRules) {
        registerProblems(rules);
      }
    }

    void registerProblems(List<? extends BranchBase<?>> branches) {
      int size = branches.size();
      if (size > 1) {
        boolean[] isDuplicate = new boolean[size];

        int defaultIndex = ContainerUtil.indexOf(branches, BranchBase::isDefault);
        if (defaultIndex >= 0) {
          BranchBase<?> defaultBranch = branches.get(defaultIndex);
          for (int index = 0; index < size; index++) {
            if (index != defaultIndex) {
              BranchBase<?> branch = branches.get(index);
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
          BranchBase<?> branch = branches.get(index);

          for (int otherIndex = index + 1; otherIndex < size; otherIndex++) {
            if (isDuplicate[otherIndex]) continue;
            if (++compareCount > 200) return; // avoid quadratic loop over too large list, but at least try to do something in that case
            BranchBase<?> otherBranch = branches.get(otherIndex);

            if (areDuplicates(branch, otherBranch)) {
              isDuplicate[otherIndex] = true;
              LocalQuickFix fix = branch.mergeCasesFix(otherBranch);
              if (fix != null) {
                registerProblem(otherBranch, otherBranch.getCaseBranchMessage(), fix);
              }
            }
          }
        }
      }
    }

    private void highlightDefaultDuplicate(@NotNull BranchBase<?> branch) {
      List<LocalQuickFix> fixes = new ArrayList<>();
      LocalQuickFix deleteCaseFix = branch.deleteCaseFix();
      ContainerUtil.addIfNotNull(fixes, deleteCaseFix);
      LocalQuickFix mergeWithDefaultFix = branch.mergeWithDefaultFix();
      ContainerUtil.addIfNotNull(fixes, mergeWithDefaultFix);
      if (!fixes.isEmpty()) {
        registerProblem(branch, branch.getDefaultBranchMessage(), fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    }

    private void registerProblem(@NotNull BranchBase<?> duplicate,
                                 @NotNull @InspectionMessage String message,
                                 @NotNull LocalQuickFix @NotNull ... fixes) {
      ProblemDescriptor descriptor = InspectionManager.getInstance(myHolder.getProject())
        .createProblemDescriptor(duplicate.myStatements[0], duplicate.myStatements[duplicate.myStatements.length - 1],
                                 message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 myHolder.isOnTheFly(), fixes);
      myHolder.registerProblem(descriptor);
    }
  }

  @NotNull
  private static Collection<List<Rule>> collectProbablySimilarRules(@NotNull PsiSwitchBlock switchBlock) {
    PsiCodeBlock switchBody = switchBlock.getBody();
    if (switchBody == null) {
      return Collections.emptyList();
    }

    Int2ObjectMap<List<Rule>> rulesByHash = new Int2ObjectOpenHashMap<>();
    List<String> commentTexts = new ArrayList<>();
    for (PsiElement element = switchBody.getFirstChild(); element != null; element = element.getNextSibling()) {
      TryWithIdenticalCatchesInspection.collectCommentTexts(element, commentTexts);
      if (element instanceof PsiSwitchLabeledRuleStatement ruleStatement) {
        PsiStatement body = ruleStatement.getBody();
        if (body != null) {
          //noinspection AssignmentToForLoopParameter
          element = collectCommentsUntilNewLine(commentTexts, element);
          Rule rule = new Rule(ruleStatement, body, ArrayUtilRt.toStringArray(commentTexts));
          commentTexts.clear();
          rulesByHash.computeIfAbsent(rule.hash(), __ -> new ArrayList<>()).add(rule);
        }
      }
    }
    return new ArrayList<>(rulesByHash.values());
  }

  @NotNull
  private static PsiElement collectCommentsUntilNewLine(List<String> commentTexts, PsiElement element) {
    List<String> mightBeCommentTexts = new ArrayList<>();
    for (PsiElement currentSibling = element.getNextSibling(); currentSibling!=null; currentSibling = currentSibling.getNextSibling()) {
      if (currentSibling instanceof PsiWhiteSpace whiteSpace) {
        if (whiteSpace.textContains('\n')) {
          commentTexts.addAll(mightBeCommentTexts);
          return currentSibling;
        }
        else {
          continue;
        }
      }
      else if (currentSibling instanceof PsiComment) {
        TryWithIdenticalCatchesInspection.collectCommentTexts(currentSibling, mightBeCommentTexts);
        continue;
      }
      return currentSibling;
    }
    return element;
  }

  @NotNull
  static Collection<List<Branch>> collectProbablySimilarBranches(@NotNull PsiSwitchBlock switchBlock) {
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) return Collections.emptyList();

    List<PsiStatement> statementList = null;
    Comments comments = new Comments();

    Branch previousBranch = null;
    Int2ObjectMap<List<Branch>> branchesByHash = new Int2ObjectOpenHashMap<>();
    for (PsiElement child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiSwitchLabelStatement switchLabel) {
        previousBranch = addBranchToMap(branchesByHash, statementList, hasImplicitBreak(switchLabel), comments, previousBranch);

        statementList = null;
        comments.addFrom(switchLabel);
      }
      else if (child instanceof PsiStatement statement) {
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
    return List.copyOf(branchesByHash.values());
  }

  @Nullable
  private static Branch addBranchToMap(@NotNull Int2ObjectMap<List<Branch>> branchesByHash,
                                       @Nullable List<PsiStatement> statementList,
                                       boolean hasImplicitBreak,
                                       @NotNull Comments comments,
                                       @Nullable Branch previousBranch) {
    if (statementList == null || statementList.isEmpty()) {
      return previousBranch;
    }
    PsiSwitchLabelStatement[] labels = Branch.collectLabels(statementList.get(0));
    if (labels.length == 0) {
      return previousBranch; // the code without a label is not allowed in 'switch', just ignore it
    }
    Branch branch = new Branch(labels, statementList, hasImplicitBreak, comments.fetchTexts());
    if (!branch.canFallThrough() && (previousBranch == null || !previousBranch.canFallThrough())) {
      int hash = branch.hash();
      List<Branch> branches = branchesByHash.get(hash);
      if (branches == null) branchesByHash.put(hash, branches = new ArrayList<>());
      branches.add(branch);
    }
    return branch;
  }

  static boolean areDuplicates(@NotNull BranchBase<?> branch, @NotNull BranchBase<?> otherBranch) {
    if (branch.isSimpleExit() != otherBranch.isSimpleExit() ||
        branch.canFallThrough() != otherBranch.canFallThrough() ||
        branch.effectiveLength() != otherBranch.effectiveLength()) {
      return false;
    }

    Match match = branch.match(otherBranch);
    if (match != null) {
      Match otherMatch = otherBranch.match(branch);
      if (otherMatch != null) {
        if (branch.isSimpleExit() &&
            otherBranch.isSimpleExit() &&
            !Arrays.equals(branch.myCommentTexts, otherBranch.myCommentTexts)) {
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

  @Contract("_,null -> false")
  private static boolean isRedundantComment(@NotNull Set<String> existingComments, @Nullable PsiElement element) {
    if (element instanceof PsiComment) {
      String text = TryWithIdenticalCatchesInspection.getCommentText((PsiComment)element);
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
      return JavaBundle.message("inspection.duplicate.branches.in.switch.merge.fix.family.name");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.duplicate.branches.in.switch.merge.fix.name", mySwitchLabelText);
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
      return JavaBundle.message("inspection.duplicate.branches.in.switch.merge.with.default.fix.name");
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

  /**
   * Used to merge 'case null -> ...' with 'default -> ...' to get as a result 'case null, default -> ...'
   */
  private static class MergeWithDefaultRuleFix extends MergeWithDefaultBranchFix {
    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      RuleFixContext context = new RuleFixContext();
      if (context.prepare(descriptor.getStartElement(), BranchBase::isDefault)) {
        context.copyCaseValues(true);
        context.deleteRedundantComments();
        context.deleteRule();
      }
    }
  }

  private static class DeleteRedundantBranchFix implements LocalQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.duplicate.branches.in.switch.delete.fix.name");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.duplicate.branches.in.switch.delete.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      BranchFixContext context = new BranchFixContext();
      if (context.prepare(descriptor.getStartElement(), BranchBase::isDefault)) {
        context.deleteBranchLabel();
        if (!context.myBranchToDelete.hasSingleNullCase()) {
          /*
           switch (obj) {
             case R():
             case null:
             case S():
               <caret>return 42; // Branch in 'switch' is a duplicate of the default branch
             case String s:
               return 0;
             default:
               return 42;
           }

           The 'case R():' and 'case S():' statements can be removed as redundant,
           because the corresponding branch is a duplicate of the default branch.
           But the 'default' case does not handle null values, so we cannot delete
           the 'case null:' and the 'return 42;' statement.

           See com.intellij.java.codeInspection.DuplicateBranchesInSwitchFixTest [DeleteRedundantBranch7.java]
          */
          context.deleteStatements();
        }
      }
    }
  }

  private static class BranchFixContext {
    private Branch myBranchToDelete;
    private Branch myBranchToMergeWith;
    private List<PsiElement> myBranchPrefixToMove;
    private PsiSwitchLabelStatement myLabelToMergeWith;
    private Set<String> myCommentsToMergeWith;
    private PsiElement myNextFromLabelToMergeWith;

    private boolean prepare(PsiElement startElement, Predicate<? super Branch> shouldMergeWith) {
      PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(startElement, PsiSwitchBlock.class);
      if (switchBlock == null) return false;

      List<Branch> candidateBranches = null;
      for (List<Branch> branches : collectProbablySimilarBranches(switchBlock)) {
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

      myCommentsToMergeWith = ContainerUtil.immutableSet(myBranchToMergeWith.myCommentTexts);
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
      if (PsiUtil.isLanguageLevel14OrHigher(moveTarget) && moveTarget instanceof PsiSwitchLabelStatement labelStatement &&
          myBranchToDelete.canCopyCaseValues() && !SwitchUtils.isCaseNull(labelStatement)) {
        for (PsiElement element : myBranchPrefixToMove) {
          if (element instanceof PsiSwitchLabelStatement statement) {
            if (SwitchUtils.isCaseNull(statement) && myLabelToMergeWith.isDefaultCase()) {
              copyCaseValues(statement, myLabelToMergeWith, true);
            }
            else {
              copyCaseValues(statement, labelStatement, false);
            }
          }
        }
      }
      else {
        if (myBranchToMergeWith.isDefault()) {
          for (PsiElement current = lastElementToMove; current != firstElementToMove; current = current.getPrevSibling()) {
            if (current instanceof PsiWhiteSpace) continue;
            if (current instanceof PsiSwitchLabelStatement labelStatement && SwitchUtils.isCaseNull(labelStatement)) {
              copyCaseValues(labelStatement, myLabelToMergeWith, true);
            }
            else {
              moveTarget.getParent().addAfter(current, moveTarget);
            }
          }
        }
        else {
          moveTarget.getParent().addRangeAfter(firstElementToMove, lastElementToMove, moveTarget);
        }
      }
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
          deleteKeepingCaseNull(toDelete.get(i), tracker, false);
        }
        deleteKeepingCaseNull(toDelete.get(size - 1), tracker, true);
      }
    }

    /**
     * Deletes the given PsiElement if it's not 'case null:'. The 'default' label does not handle null values,
     * so 'case null' is not redundant and cannot be removed.
     * <p>
     * We cannot get labels like 'case String s, null' or 'case "hello", null' here.
     *
     * @param element             element to delete
     * @param ct                  CommentTracker to use
     * @param needRestoreComments if {@code true} {@link CommentTracker#deleteAndRestoreComments(PsiElement)} is called,
     *                            {@link CommentTracker#delete(PsiElement)} otherwise
     */
    private static void deleteKeepingCaseNull(@NotNull PsiElement element, @NotNull CommentTracker ct, boolean needRestoreComments) {
      if (!(element instanceof PsiSwitchLabelStatement labelStatement) || !SwitchUtils.isCaseNull(labelStatement)) {
        if (needRestoreComments) {
          ct.delete(element);
        }
        else {
          new CommentTracker().deleteAndRestoreComments(element);
        }
      }
    }

    private static @Nullable PsiCaseLabelElementList getCaseLabelElementList(@NotNull PsiSwitchLabelStatement label) {
      if (label.isDefaultCase()) {
        PsiElementFactory factory = PsiElementFactory.getInstance(label.getProject());
        PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)factory.createStatementFromText("case default:", null);
        PsiSwitchLabelStatement newLabelStatement = (PsiSwitchLabelStatement)label.replace(labelStatement);
        return newLabelStatement.getCaseLabelElementList();
      }
      return label.getCaseLabelElementList();
    }

    private static void copyCaseValues(@NotNull PsiSwitchLabelStatement from,
                                       @NotNull PsiSwitchLabelStatement to,
                                       boolean mergeWithDefault) {
      @Nullable PsiCaseLabelElementList fromCaseLabelElementList = getCaseLabelElementList(from);
      @Nullable PsiCaseLabelElementList toCaseLabelElementList = getCaseLabelElementList(to);
      DuplicateBranchesInSwitchInspection.copyCaseValues(fromCaseLabelElementList, toCaseLabelElementList, mergeWithDefault);
    }
  }

  private static abstract class BranchBase<T extends PsiSwitchLabelStatementBase> {
    @NotNull protected final T myLabel;
    protected final PsiStatement @NotNull [] myStatements;
    protected final String @NotNull [] myCommentTexts;
    private final boolean myIsDefault;
    private DuplicatesFinder myFinder;
    protected final boolean myCanDeleteRedundantBranch;

    BranchBase(T @NotNull [] labels, PsiStatement @NotNull [] statements, String @NotNull [] commentTexts) {
      LOG.assertTrue(labels.length != 0, "labels.length");
      LOG.assertTrue(statements.length != 0, "statements.length");

      myLabel = labels[0];
      myStatements = statements;
      myCommentTexts = commentTexts;
      myIsDefault = ContainerUtil.exists(labels, label -> label.isDefaultCase() || SwitchUtils.isCaseNullDefault(label));
      myCanDeleteRedundantBranch = labels.length > 1 || !ContainerUtil.exists(labels, BranchBase::hasNullCase);
    }

    private static boolean hasNullCase(@NotNull PsiSwitchLabelStatementBase label) {
      PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
      if (labelElementList == null) return false;
      PsiCaseLabelElement[] elements = labelElementList.getElements();
      return ContainerUtil.exists(elements, ExpressionUtils::isNullLiteral);
    }

    boolean isDefault() {
      return myIsDefault;
    }

    @Nullable
    String getSwitchLabelText() {
      return getSwitchLabelText(myLabel);
    }

    abstract boolean isSimpleExit();

    abstract boolean canFallThrough();

    abstract boolean canMergeBranch();

    int effectiveLength() {
      if (myStatements.length == 1 && myStatements[0] instanceof PsiBlockStatement) {
        return ((PsiBlockStatement)myStatements[0]).getCodeBlock().getStatementCount();
      }
      return myStatements.length;
    }

    int hash() {
      return hashStatements(myStatements);
    }

    @Nullable
    abstract LocalQuickFix mergeCasesFix(BranchBase<?> otherBranch);

    @Nullable
    abstract LocalQuickFix deleteCaseFix();

    @Nullable
    abstract LocalQuickFix mergeWithDefaultFix();

    @Nullable
    Match match(BranchBase<?> other) {
      return getFinder().isDuplicate(other.myStatements[0], true);
    }

    @NotNull
    private DuplicatesFinder getFinder() {
      if (myFinder == null) {
        myFinder = createFinder(myStatements);
      }
      return myFinder;
    }

    @InspectionMessage String getCaseBranchMessage() {
      return JavaBundle.message("inspection.duplicate.branches.in.switch.message");
    }

    @InspectionMessage String getDefaultBranchMessage() {
      return JavaBundle.message("inspection.duplicate.branches.in.switch.default.message");
    }

    @Override
    public String toString() {
      return StringUtil.notNullize(getSwitchLabelText());
    }

    @NotNull
    private static DuplicatesFinder createFinder(PsiStatement @NotNull [] statements) {
      Project project = statements[0].getProject();
      InputVariables noVariables = new InputVariables(Collections.emptyList(), project, new LocalSearchScope(statements), false, Collections.emptySet());
      return new DuplicatesFinder(statements, noVariables, null, Collections.emptyList());
    }

    @Nullable
    static String getSwitchLabelText(@Nullable PsiSwitchLabelStatementBase switchLabel) {
      if (switchLabel != null) {
        if (switchLabel.isDefaultCase()) {
          return PsiKeyword.DEFAULT;
        }
        PsiCaseLabelElementList labelElementList = switchLabel.getCaseLabelElementList();
        if (labelElementList != null) {
          PsiCaseLabelElement[] expressions = labelElementList.getElements();
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
      if (element instanceof PsiBlockStatement) {
        return hashStatements(((PsiBlockStatement)element).getCodeBlock().getStatements()) * 31
               + JavaElementType.BLOCK_STATEMENT.getIndex();
      }
      int hash = 0;
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
      IElementType type = PsiUtilCore.getElementType(element);
      if (type != null) {
        hash = hash * 31 + type.getIndex();
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

    static int hashStatements(PsiStatement @NotNull [] statements) {
      int hash = statements.length;
      for (PsiStatement statement : statements) {
        hash = hash * 31 + hashElement(statement, 2); // Don't want to hash the whole PSI tree because it might be quite slow
      }
      return hash;
    }
  }

  private static class Branch extends BranchBase<PsiSwitchLabelStatement> {
    private static final PsiSwitchLabelStatement[] EMPTY_LABELS_ARRAY = new PsiSwitchLabelStatement[0];
    private final boolean myIsSimpleExit;
    private final boolean myCanFallThrough;
    private final boolean myCanMergeBranch;
    private final boolean myHasSingleNullCase;
    private final boolean myCanCopyCaseValues;

    Branch(PsiSwitchLabelStatement @NotNull [] labels,
           @NotNull List<PsiStatement> statementList,
           boolean hasImplicitBreak,
           String @NotNull [] comments) {
      super(labels, statementsWithoutTrailingBreak(statementList), comments);

      int lastIndex = statementList.size() - 1;
      PsiStatement lastStatement = statementList.get(lastIndex);
      myCanFallThrough = !hasImplicitBreak && ControlFlowUtils.statementMayCompleteNormally(lastStatement);
      myIsSimpleExit = lastIndex == 0 && isSimpleExit(lastStatement);
      myCanMergeBranch = calculateCanMergeBranches(labels);
      myHasSingleNullCase = ContainerUtil.exists(labels, SwitchUtils::isCaseNull);
      myCanCopyCaseValues = ContainerUtil.all(labels, label -> Rule.calculateCanMergeBranches(label));
    }

    @Override
    boolean canFallThrough() {
      return myCanFallThrough;
    }

    private static boolean calculateCanMergeBranches(PsiSwitchLabelStatement @NotNull [] labels) {
      boolean java20plus = PsiUtil.getLanguageLevel(labels[0]).isAtLeast(LanguageLevel.JDK_20_PREVIEW);
      for (PsiSwitchLabelStatement label : labels) {
        PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
        if (labelElementList == null) continue;
        PsiCaseLabelElement[] elements = labelElementList.getElements();
        for (PsiCaseLabelElement element : elements) {
          if (PsiTreeUtil.instanceOf(element, PsiGuardedPattern.class, PsiPatternGuard.class) ||
              element instanceof PsiPattern && (!java20plus || JavaPsiPatternUtil.containsPatternVariable(element))) {
            return false;
          }
        }
      }
      return true;
    }

    @Override
    boolean canMergeBranch() {
      return myCanMergeBranch;
    }

    boolean canCopyCaseValues() {
      return myCanCopyCaseValues;
    }

    boolean hasSingleNullCase() {
      return myHasSingleNullCase;
    }

    @Override
    boolean isSimpleExit() {
      return myIsSimpleExit;
    }

    @Nullable
    @Override
    LocalQuickFix mergeCasesFix(BranchBase<?> otherBranch) {
      if (!otherBranch.canMergeBranch()) return null;
      String switchLabelText = getSwitchLabelText();
      if (switchLabelText == null) return null;
      return myCanMergeBranch ? new MergeBranchesFix(switchLabelText) : null;
    }

    @Nullable
    @Override
    LocalQuickFix mergeWithDefaultFix() {
      return myCanMergeBranch ? new MergeWithDefaultBranchFix() : null;
    }

    @Nullable
    @Override
    LocalQuickFix deleteCaseFix() {
      return myCanDeleteRedundantBranch ? new DeleteRedundantBranchFix() : null;
    }

    // 'switch' labels with comments and spaces
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
          statement instanceof PsiYieldStatement ||
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

    private static PsiSwitchLabelStatement[] collectLabels(PsiStatement statement) {
      List<PsiSwitchLabelStatement> labels = new ArrayList<>();
      for (PsiElement element = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
           element instanceof PsiSwitchLabelStatement;
           element = PsiTreeUtil.getPrevSiblingOfType(element, PsiStatement.class)) {
        labels.add((PsiSwitchLabelStatement)element);
      }
      Collections.reverse(labels);
      return labels.toArray(EMPTY_LABELS_ARRAY);
    }

    private static PsiStatement[] statementsWithoutTrailingBreak(List<? extends PsiStatement> statementList) {
      // trailing 'break' is taken into account in myCanFallThrough
      int lastIndex = statementList.size() - 1;
      PsiStatement lastStatement = statementList.get(lastIndex);
      if (lastIndex > 0 && isBreakWithoutLabel(lastStatement)) {
        statementList = statementList.subList(0, lastIndex);
      }
      return statementList.toArray(PsiStatement.EMPTY_ARRAY);
    }
  }

  private static class Comments {
    private final List<String> myTexts = new ArrayList<>();
    private final List<PsiElement> myPending = new ArrayList<>();

    String[] fetchTexts() {
      String[] result = ArrayUtilRt.toStringArray(myTexts);
      myTexts.clear();
      return result;
    }

    void addFrom(PsiStatement statement) {
      // The comments followed by a switch label are attached to that switch label.
      // They're pending until we know if the next statement is a label or not.
      for (PsiElement pending : myPending) {
        TryWithIdenticalCatchesInspection.collectCommentTexts(pending, myTexts);
      }
      myPending.clear();
      TryWithIdenticalCatchesInspection.collectCommentTexts(statement, myTexts);
    }

    public void addPending(PsiElement element) {
      myPending.add(element);
    }
  }

  private static class Rule extends BranchBase<PsiSwitchLabeledRuleStatement> {
    private final boolean myIsSimpleExit;
    private final boolean myCanMergeBranches;
    private final boolean myCanMergeWithDefaultBranch;

    Rule(@NotNull PsiSwitchLabeledRuleStatement rule, @NotNull PsiStatement body, String @NotNull [] commentTexts) {
      super(new PsiSwitchLabeledRuleStatement[]{rule}, new PsiStatement[]{body}, commentTexts);
      myIsSimpleExit = body instanceof PsiExpressionStatement || body instanceof PsiThrowStatement;
      myCanMergeBranches = calculateCanMergeBranches(rule);
      myCanMergeWithDefaultBranch = SwitchUtils.isCaseNull(rule);
    }

    public static boolean calculateCanMergeBranches(@NotNull PsiSwitchLabelStatementBase rule) {
      PsiCaseLabelElementList labelElementList = rule.getCaseLabelElementList();
      if (labelElementList == null) return false;
      PsiCaseLabelElement[] elements = labelElementList.getElements();
      return !ContainerUtil.exists(elements, element -> element instanceof PsiPattern ||
                                                        element instanceof PsiPatternGuard ||
                                                        ExpressionUtils.isNullLiteral(element));
    }

    @Override
    boolean canMergeBranch() {
      return myCanMergeBranches;
    }

    @Override
    boolean isSimpleExit() {
      return myIsSimpleExit;
    }

    @Override
    boolean canFallThrough() {
      return false;
    }

    @Nullable
    @Override
    LocalQuickFix mergeCasesFix(BranchBase<?> otherBranch) {
      if (!otherBranch.canMergeBranch()) return null;
      String switchLabelText = getSwitchLabelText();
      if (switchLabelText == null) return null;
      return myCanMergeBranches ? new MergeRulesFix(switchLabelText) : null;
    }

    @Nullable
    @Override
    LocalQuickFix mergeWithDefaultFix() {
      return myCanMergeWithDefaultBranch ? new MergeWithDefaultRuleFix() : null;
    }

    @Nullable
    @Override
    LocalQuickFix deleteCaseFix() {
      return myCanDeleteRedundantBranch ? new DeleteRedundantRuleFix() : null;
    }
  }

  private static class MergeRulesFix implements LocalQuickFix {
    @NotNull private final String mySwitchLabelText;

    MergeRulesFix(@NotNull String switchLabelText) {
      mySwitchLabelText = switchLabelText;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.duplicate.branches.in.switch.merge.fix.family.name");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.duplicate.branches.in.switch.merge.fix.name", mySwitchLabelText);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      RuleFixContext context = new RuleFixContext();
      if (context.prepare(descriptor.getStartElement(), rule -> mySwitchLabelText.equals(rule.getSwitchLabelText()))) {
        context.copyCaseValues(false);
        context.deleteRedundantComments();
        context.deleteRule();
      }
    }
  }

  private static class DeleteRedundantRuleFix implements LocalQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.duplicate.branches.in.switch.delete.fix.name");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.duplicate.branches.in.switch.delete.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      RuleFixContext context = new RuleFixContext();
      if (context.prepare(descriptor.getStartElement(), Rule::isDefault)) {
        context.deleteRedundantComments();
        context.deleteRule();
      }
    }
  }

  private static class RuleFixContext {
    private Rule myRuleToDelete;
    private Rule myRuleToMergeWith;
    private Set<String> myCommentsToMergeWith;

    boolean prepare(PsiElement startElement, Predicate<? super Rule> shouldMergeWith) {
      if (startElement != null) {
        PsiSwitchLabeledRuleStatement ruleStatement = ObjectUtils.tryCast(startElement.getParent(), PsiSwitchLabeledRuleStatement.class);
        if (ruleStatement != null) {
          PsiSwitchBlock switchBlock = ruleStatement.getEnclosingSwitchBlock();
          if (switchBlock != null) {
            List<Rule> candidateRules = null;
            for (List<Rule> rules : collectProbablySimilarRules(switchBlock)) {
              myRuleToDelete = ContainerUtil.find(rules, r -> r.myLabel == ruleStatement);
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
      myCommentsToMergeWith = ContainerUtil.immutableSet(myRuleToMergeWith.myCommentTexts);
      return true;
    }

    public void deleteRedundantComments() {
      List<PsiElement> redundantComments = new ArrayList<>();

      List<PsiElement> mightBeRedundantComments = new ArrayList<>();
      for (PsiElement element = myRuleToDelete.myLabel.getNextSibling(); element != null; element = element.getNextSibling()) {
        if (element instanceof PsiWhiteSpace whiteSpace) {
          if (whiteSpace.textContains('\n')) {
            redundantComments.addAll(mightBeRedundantComments);
            break;
          }
        }
        else if (element instanceof PsiComment psiComment) {
          if (isRedundantComment(myCommentsToMergeWith, psiComment)) {
            mightBeRedundantComments.add(psiComment);
          }
        }
      }

      mightBeRedundantComments.clear();
      for (PsiElement element = myRuleToDelete.myLabel.getPrevSibling(); element != null; element = element.getPrevSibling()) {
        if (element instanceof PsiWhiteSpace whiteSpace) {
          if (whiteSpace.textContains('\n')) {
            redundantComments.addAll(mightBeRedundantComments);
            mightBeRedundantComments.clear();
          }
          continue;
        }
        else if (element instanceof PsiComment psiComment) {
          if (isRedundantComment(myCommentsToMergeWith, psiComment)) {
            mightBeRedundantComments.add(psiComment);
            continue;
          }
        }
        break;
      }
      redundantComments.forEach(PsiElement::delete);
    }

    @Nullable PsiCaseLabelElementList getCaseLabelElementList(@NotNull PsiSwitchLabeledRuleStatement ruleStatement) {
      if (ruleStatement.isDefaultCase()) {
        PsiElementFactory factory = PsiElementFactory.getInstance(ruleStatement.getProject());
        PsiSwitchLabeledRuleStatement labelStatement =
          (PsiSwitchLabeledRuleStatement)factory.createStatementFromText("case default->{}", null);
        PsiStatement body = ruleStatement.getBody();
        if (body != null) {
          Objects.requireNonNull(labelStatement.getBody()).replace(body);
        }
        PsiSwitchLabeledRuleStatement newLabelStatement = (PsiSwitchLabeledRuleStatement)ruleStatement.replace(labelStatement);
        return newLabelStatement.getCaseLabelElementList();
      }
      return ruleStatement.getCaseLabelElementList();
    }

    void copyCaseValues(boolean mergeWithDefault) {
      @Nullable PsiCaseLabelElementList caseValuesToMergeWith = getCaseLabelElementList(myRuleToMergeWith.myLabel);
      @Nullable PsiCaseLabelElementList caseValuesToDelete = getCaseLabelElementList(myRuleToDelete.myLabel);
      DuplicateBranchesInSwitchInspection.copyCaseValues(caseValuesToDelete, caseValuesToMergeWith, mergeWithDefault);
    }

    void deleteRule() {
      CommentTracker tracker = new CommentTracker();
      PsiTreeUtil.processElements(myRuleToDelete.myLabel, child -> {
        if (isRedundantComment(myCommentsToMergeWith, child)) {
          tracker.markUnchanged(child);
        }
        return true;
      });

      tracker.deleteAndRestoreComments(myRuleToDelete.myLabel);
    }
  }

  public static void copyCaseValues(@Nullable PsiCaseLabelElementList from,
                                    @Nullable PsiCaseLabelElementList to,
                                    boolean mergeWithDefault) {
    if (from == null || to == null) return;
    for (PsiCaseLabelElement caseValue : from.getElements()) {
      if (mergeWithDefault) {
        to.addBefore(caseValue, to.getFirstChild());
      }
      else {
        to.addAfter(caseValue, to.getLastChild());
      }
    }
  }
}