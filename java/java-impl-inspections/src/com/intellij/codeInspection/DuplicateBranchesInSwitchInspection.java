// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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

/**
 * @author Pavel.Dolgov
 */
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
    public void visitSwitchStatement(PsiSwitchStatement switchStatement) {
      super.visitSwitchStatement(switchStatement);

      visitSwitchBlock(switchStatement);
    }

    @Override
    public void visitSwitchExpression(PsiSwitchExpression switchExpression) {
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
              highlightDuplicate(otherBranch, branch);
            }
          }
        }
      }
    }

    private static boolean isMergeCasesFixAvailable(@NotNull BranchBase<?> duplicate, @NotNull BranchBase<?> original) {
      if (duplicate.myIsGuardedOrParenthesizedPattern || original.myIsGuardedOrParenthesizedPattern) return false;
      if (duplicate.myIsTypeTestPattern != original.myIsTypeTestPattern) {
        return duplicate.myIsNull || original.myIsNull;
      }
      return !duplicate.myIsTypeTestPattern;
    }


    private void highlightDuplicate(@NotNull BranchBase<?> duplicate, @NotNull BranchBase<?> original) {
      LocalQuickFix fix = isMergeCasesFixAvailable(duplicate, original) ? original.newMergeCasesFix() : null;
      registerProblem(duplicate, duplicate.getCaseBranchMessage(), fix);
    }

    private void highlightDefaultDuplicate(@NotNull BranchBase branch) {
      registerProblem(branch, branch.getDefaultBranchMessage(), branch.newDeleteCaseFix(), branch.newMergeWithDefaultFix());
    }

    private void registerProblem(@NotNull BranchBase duplicate, @NotNull @InspectionMessage String message, LocalQuickFix @NotNull ... fixes) {
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
      if (!(element instanceof PsiSwitchLabeledRuleStatement)) {
        TryWithIdenticalCatchesInspection.collectCommentTexts(element, commentTexts);
        continue;
      }
      PsiSwitchLabeledRuleStatement ruleStatement = (PsiSwitchLabeledRuleStatement)element;
      PsiStatement body = ruleStatement.getBody();
      if (body != null) {
        TryWithIdenticalCatchesInspection.collectCommentTexts(ruleStatement, commentTexts);
        Rule rule = new Rule(ruleStatement, body, ArrayUtilRt.toStringArray(commentTexts));
        commentTexts.clear();
        int hash = rule.hash();
        rulesByHash.computeIfAbsent(hash, __ -> new ArrayList<>()).add(rule);
      }
    }
    return new ArrayList<>(rulesByHash.values());
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

  static boolean areDuplicates(@NotNull BranchBase branch, @NotNull BranchBase otherBranch) {
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

  private static abstract class BranchBase<T extends PsiSwitchLabelStatementBase> {
    @NotNull protected final T myLabel;
    protected final PsiStatement @NotNull [] myStatements;
    protected final String @NotNull [] myCommentTexts;
    private final boolean myIsDefault;
    private final boolean myIsTypeTestPattern;
    private final boolean myIsGuardedOrParenthesizedPattern;
    private final boolean myIsNull;
    private DuplicatesFinder myFinder;

    BranchBase(T @NotNull [] labels, PsiStatement @NotNull [] statements, String @NotNull [] commentTexts) {
      LOG.assertTrue(labels.length != 0, "labels.length");
      LOG.assertTrue(statements.length != 0, "statements.length");

      myLabel = labels[0];
      myStatements = statements;
      myCommentTexts = commentTexts;
      myIsDefault = ContainerUtil.find(labels, PsiSwitchLabelStatementBase::isDefaultCase) != null;
      myIsTypeTestPattern = isPatternBy(labels, PsiTypeTestPattern.class);
      myIsGuardedOrParenthesizedPattern = isPatternBy(labels, PsiGuardedPattern.class, PsiParenthesizedPattern.class);
      myIsNull = isNull(labels);
    }

    private boolean isPatternBy(T @NotNull [] labels, Class<? extends PsiPattern>... classes) {
      return ContainerUtil.find(labels, label -> {
        PsiCaseLabelElement[] labelElements = getCaseLabelElements(label);
        if (labelElements == null) return false;
        return ContainerUtil.exists(labelElements, labelElement -> PsiTreeUtil.instanceOf(labelElement, classes));
        }) != null;
    }

    private boolean isNull(T @NotNull [] labels) {
      if (labels.length != 1) return false;
      PsiCaseLabelElement[] labelElements = getCaseLabelElements(labels[0]);
      if (labelElements == null) return false;
      return labelElements.length == 1 && ExpressionUtils.isNullLiteral(ObjectUtils.tryCast(labelElements[0], PsiExpression.class));
    }

    private PsiCaseLabelElement[] getCaseLabelElements(T label) {
      PsiSwitchLabelStatementBase labelStatementBase = ObjectUtils.tryCast(label, PsiSwitchLabelStatementBase.class);
      if (labelStatementBase == null) return null;
      PsiCaseLabelElementList labelElementList = labelStatementBase.getCaseLabelElementList();
      if (labelElementList == null) return null;
      return labelElementList.getElements();
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
    abstract LocalQuickFix newMergeCasesFix();

    abstract LocalQuickFix newDeleteCaseFix();

    abstract LocalQuickFix newMergeWithDefaultFix();

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

    Branch(PsiSwitchLabelStatement @NotNull [] labels, @NotNull List<PsiStatement> statementList, boolean hasImplicitBreak, String @NotNull [] comments) {
      super(labels, statementsWithoutTrailingBreak(statementList), comments);

      int lastIndex = statementList.size() - 1;
      PsiStatement lastStatement = statementList.get(lastIndex);
      myCanFallThrough = !hasImplicitBreak && ControlFlowUtils.statementMayCompleteNormally(lastStatement);
      myIsSimpleExit = lastIndex == 0 && isSimpleExit(lastStatement);
    }

    @Override
    boolean canFallThrough() {
      return myCanFallThrough;
    }

    @Override
    boolean isSimpleExit() {
      return myIsSimpleExit;
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

    Rule(@NotNull PsiSwitchLabeledRuleStatement rule, @NotNull PsiStatement body, String @NotNull [] commentTexts) {
      super(new PsiSwitchLabeledRuleStatement[]{rule}, new PsiStatement[]{body}, commentTexts);
      myIsSimpleExit = body instanceof PsiExpressionStatement || body instanceof PsiThrowStatement;
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
    LocalQuickFix newMergeCasesFix() {
      String switchLabelText = getSwitchLabelText();
      return switchLabelText != null ? new MergeRulesFix(switchLabelText) : null;
    }

    @Override
    LocalQuickFix newMergeWithDefaultFix() {
      return null;
    }

    @Override
    LocalQuickFix newDeleteCaseFix() {
      return new DeleteRedundantRuleFix();
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
        context.copyCaseValues();
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
      myCommentsToMergeWith = ContainerUtil.set(myRuleToMergeWith.myCommentTexts);
      return true;
    }

    void copyCaseValues() {
      @Nullable PsiCaseLabelElementList caseValuesToMergeWith = myRuleToMergeWith.myLabel.getCaseLabelElementList();
      @Nullable PsiCaseLabelElementList caseValuesToDelete = myRuleToDelete.myLabel.getCaseLabelElementList();
      if (caseValuesToDelete != null && caseValuesToMergeWith != null) {
        for (PsiCaseLabelElement caseValue : caseValuesToDelete.getElements()) {
          caseValuesToMergeWith.addAfter(caseValue, caseValuesToMergeWith.getLastChild());
        }
      }
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
}