// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.TrackingDfaMemoryState.MemoryStateChange;
import com.intellij.codeInspection.dataFlow.TrackingDfaMemoryState.Relation;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

@SuppressWarnings("SuspiciousNameCombination")
public class TrackingRunner extends StandardDataFlowRunner {
  private final List<MemoryStateChange> myHistoryForContext = new ArrayList<>();
  private final PsiExpression myExpression;
  private final List<DfaInstructionState> afterStates = new ArrayList<>();
  private final List<TrackingDfaMemoryState> killedStates = new ArrayList<>();

  private TrackingRunner(boolean unknownMembersAreNullable, @Nullable PsiElement context, PsiExpression expression) {
    super(unknownMembersAreNullable, context);
    myExpression = expression;
  }

  @Override
  protected void beforeInstruction(Instruction instruction) {
    afterStates.clear();
    killedStates.clear();
  }

  @Override
  protected void afterInstruction(Instruction instruction) {
    if (afterStates.size() <= 1 && killedStates.isEmpty()) return;
    Map<Instruction, List<TrackingDfaMemoryState>> instructionToState =
      StreamEx.of(afterStates).mapToEntry(s -> s.getInstruction(), s -> (TrackingDfaMemoryState)s.getMemoryState()).grouping();
    if (instructionToState.size() <= 1 && killedStates.isEmpty()) return;
    instructionToState.forEach((target, memStates) -> {
      List<TrackingDfaMemoryState> bridgeChanges =
        StreamEx.of(afterStates).filter(s -> s.getInstruction() != target)
          .map(s -> ((TrackingDfaMemoryState)s.getMemoryState()))
          .append(killedStates)
          .toList();
      for (TrackingDfaMemoryState state : memStates) {
        state.addBridge(instruction, bridgeChanges);
      }
    });
  }

  @NotNull
  @Override
  protected DfaMemoryState createMemoryState() {
    return new TrackingDfaMemoryState(getFactory());
  }

  @NotNull
  @Override
  protected DfaInstructionState[] acceptInstruction(@NotNull InstructionVisitor visitor, @NotNull DfaInstructionState instructionState) {
    Instruction instruction = instructionState.getInstruction();
    TrackingDfaMemoryState memState = (TrackingDfaMemoryState)instructionState.getMemoryState().createCopy();
    DfaInstructionState[] states = super.acceptInstruction(visitor, instructionState);
    for (DfaInstructionState state : states) {
      afterStates.add(state);
      ((TrackingDfaMemoryState)state.getMemoryState()).recordChange(instruction, memState);
    }
    if (states.length == 0) {
      killedStates.add(memState);
    }
    if (instruction instanceof ExpressionPushingInstruction) {
      ExpressionPushingInstruction pushing = (ExpressionPushingInstruction)instruction;
      if (pushing.getExpression() == myExpression && pushing.getExpressionRange() == null) {
        for (DfaInstructionState state : states) {
          myHistoryForContext.addAll(((TrackingDfaMemoryState)state.getMemoryState()).getHistory());
        }
      }
    }
    return states;
  }

  public static List<CauseItem> findProblemCause(boolean unknownAreNullables,
                                                 boolean ignoreAssertions,
                                                 PsiExpression expression,
                                                 DfaProblemType type) {
    PsiElement body = DfaUtil.getDataflowContext(expression);
    if (body == null) return Collections.emptyList();
    TrackingRunner runner = new TrackingRunner(unknownAreNullables, body, expression);
    StandardInstructionVisitor visitor = new StandardInstructionVisitor(true);
    RunnerResult result = runner.analyzeMethodRecursively(body, visitor, ignoreAssertions);
    if (result != RunnerResult.OK) return Collections.emptyList();
    CauseItem cause = null;
    for (MemoryStateChange history : runner.myHistoryForContext) {
      CauseItem root = findCauseChain(expression, history, type);
      if (cause == null) {
        cause = root;
      } else {
        cause = cause.merge(root);
        if (cause == null) return Collections.emptyList();
      }
    }
    return Collections.singletonList(cause);
  }

  public abstract static class DfaProblemType {
    public abstract String toString();

    CauseItem[] findCauses(PsiExpression expression, MemoryStateChange history) {
      return new CauseItem[0];
    }
  }

  public static class CauseItem {
    final @NotNull List<CauseItem> myChildren;
    final @NotNull DfaProblemType myProblem;
    final @Nullable SmartPsiFileRange myTarget;

    CauseItem(@NotNull String problem, @Nullable PsiElement target) {
      this(new CustomDfaProblemType(problem), target);
    }

    CauseItem(@NotNull DfaProblemType problem, @Nullable PsiElement target) {
      myChildren = new ArrayList<>();
      myProblem = problem;
      if (target != null) {
        PsiFile file = target.getContainingFile();
        myTarget = SmartPointerManager.getInstance(file.getProject()).createSmartPsiFileRangePointer(file, target.getTextRange());
      } else {
        myTarget = null;
      }
    }

    CauseItem(@NotNull String problem, @NotNull MemoryStateChange change) {
      this(new CustomDfaProblemType(problem), change);
    }

    CauseItem(@NotNull DfaProblemType problem, @NotNull MemoryStateChange change) {
      this(problem, change.getExpression());
    }

    void addChildren(CauseItem... causes) {
      ContainerUtil.addAllNotNull(myChildren, causes);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CauseItem item = (CauseItem)o;
      return myChildren.equals(item.myChildren) &&
             getProblemName().equals(item.getProblemName()) &&
             Objects.equals(myTarget, item.myTarget);
    }

    private String getProblemName() {
      return myProblem.toString();
    }

    @Override
    public int hashCode() {
      return Objects.hash(myChildren, getProblemName(), myTarget);
    }

    public String dump(Document doc) {
      return dump(doc, 0, null);
    }

    private String dump(Document doc, int indent, CauseItem parent) {
      String text = null;
      if (myTarget != null) {
        Segment range = myTarget.getRange();
        if (range != null) {
          text = doc.getText(TextRange.create(range));
          int lineNumber = doc.getLineNumber(range.getStartOffset());
          text += "; line#" + (lineNumber + 1);
        }
      }
      return StringUtil.repeat("  ", indent) + render(doc, parent) + (text == null ? "" : " (" + text + ")") + "\n" +
             StreamEx.of(myChildren).map(child -> child.dump(doc, indent + 1, this)).joining();
    }

    public Stream<CauseItem> children() {
      return StreamEx.of(myChildren);
    }

    @Nullable
    public PsiFile getFile() {
      return myTarget != null ? myTarget.getContainingFile() : null;
    }
    
    public Segment getTargetSegment() {
      return myTarget == null ? null : myTarget.getRange();
    }

    public String render(Document doc, CauseItem parent) {
      String title = null;
      Segment range = getTargetSegment();
      if (range != null) {
        String cause = getProblemName();
        if (cause.endsWith("#ref")) {
          int offset = range.getStartOffset();
          int number = doc.getLineNumber(offset);
          title = cause.replaceFirst("#ref$", "line #" + (number + 1));
        }
      }
      if (title == null) {
        title = toString();
      }
      int childIndex = parent == null ? 0 : parent.myChildren.indexOf(this);
      if (childIndex > 0) {
        title = (parent.myProblem instanceof PossibleExecutionDfaProblemType ? "or " : "and ") + title; 
      } else {
        title = StringUtil.capitalize(title);
      }
      return title;
    }

    @Override
    public String toString() {
      return getProblemName().replaceFirst("#ref$", "here");
    }

    public CauseItem merge(CauseItem other) {
      if (this.equals(other)) return this;
      if (Objects.equals(this.myTarget, other.myTarget) && getProblemName().equals(other.getProblemName())) {
        if(tryMergeChildren(other.myChildren)) return this;
        if(other.tryMergeChildren(this.myChildren)) return other;
      }
      return null;
    }

    private boolean tryMergeChildren(List<CauseItem> children) {
      if (myChildren.isEmpty()) return false;
      if (myChildren.size() != 1 || !(myChildren.get(0).myProblem instanceof PossibleExecutionDfaProblemType)) {
        if (children.size() == myChildren.size()) {
          List<CauseItem> merged = StreamEx.zip(myChildren, children, CauseItem::merge).toList();
          if (!merged.contains(null)) {
            myChildren.clear();
            myChildren.addAll(merged);
            return true;
          }
        }
        insertIntoHierarchy(new CauseItem(new PossibleExecutionDfaProblemType(), (PsiElement)null));
      }
      CauseItem mergePoint = myChildren.get(0);
      if (children.isEmpty()) {
        ((PossibleExecutionDfaProblemType)mergePoint.myProblem).myComplete = false;
      }
      for (CauseItem child : children) {
        if (!mergePoint.myChildren.contains(child)) {
          mergePoint.myChildren.add(child);
        }
      }
      return true;
    }

    private void insertIntoHierarchy(CauseItem intermediate) {
      intermediate.myChildren.addAll(myChildren);
      myChildren.clear();
      myChildren.add(intermediate);
    }
  }
  
  public static class CastDfaProblemType extends DfaProblemType {
    @Override
    public CauseItem[] findCauses(PsiExpression expression, MemoryStateChange history) {
      if (expression instanceof PsiTypeCastExpression) {
        PsiType expressionType = expression.getType();
        MemoryStateChange operandPush = history.findExpressionPush(((PsiTypeCastExpression)expression).getOperand());
        if (operandPush != null) {
          return new CauseItem[]{findTypeCause(operandPush, expressionType, false)};
        }
      }
      return new CauseItem[0];
    }

    public String toString() {
      return "cast may fail";
    }
  }

  public static class NullableDfaProblemType extends DfaProblemType {
    @Override
    public CauseItem[] findCauses(PsiExpression expression, MemoryStateChange history) {
      Pair<MemoryStateChange, DfaNullability> nullability = history.findFact(history.myTopOfStack, DfaFactType.NULLABILITY);
      if (nullability.second == DfaNullability.NULLABLE || nullability.second == DfaNullability.NULL) {
        return new CauseItem[]{findNullabilityCause(history, nullability.first, nullability.second)};
      }
      return new CauseItem[0];
    }

    public String toString() {
      return "may be null";
    }
  }

  static class PossibleExecutionDfaProblemType extends DfaProblemType {
    boolean myComplete = true;

    @Override
    public String toString() {
      return myComplete ? "one of the following happens:" : "an execution might exist where:";
    }
  }
  

  public static class ValueDfaProblemType extends DfaProblemType {
    final Object myValue;

    public ValueDfaProblemType(Object value) {
      myValue = value;
    }

    @Override
    public CauseItem[] findCauses(PsiExpression expression, MemoryStateChange history) {
      return findConstantValueCause(expression, history, myValue);
    }

    @Override
    public String toString() {
      return "value is always " + myValue;
    }
  }

  static class CustomDfaProblemType extends DfaProblemType {
    private final String myMessage;

    CustomDfaProblemType(String message) {
      myMessage = message;
    }

    @Override
    public String toString() {
      return myMessage;
    }
  }

  /*
  TODO: 1. Find causes of other warnings:  
            Cause for AIOOBE
            Cause for "Contract always fails"
            Cause for "modifying an immutable collection"
            Cause for "Collection is always empty" (separate inspection now)
  TODO: 2. Describe causes in more cases:
            Warning caused by contract
            Warning caused by CustomMethodHandler
            Warning caused by polyadic math
            Warning caused by narrowing conversion
            Warning caused by unary minus
            Warning caused by final field initializer
  TODO: 3. Check how it works with:
            Inliners (notably: Stream API)
            Boxed numbers
  TODO: 4. Check for possible performance disasters (likely on some code patterns current algo might blow up)
  TODO: 5. Problem when interesting state doesn't reach the current condition, need to do something with this    
   */
  @NotNull
  private static CauseItem findCauseChain(PsiExpression expression, MemoryStateChange history, DfaProblemType type) {
    CauseItem root = new CauseItem(type, expression);
    if (history.getExpression() != expression) return root;
    root.addChildren(type.findCauses(expression, history));
    return root;
  }

  @NotNull
  private static CauseItem[] findConstantValueCause(PsiExpression expression, MemoryStateChange history, Object expectedValue) {
    if (expression instanceof PsiLiteralExpression) return new CauseItem[0];
    Object constantExpressionValue = ExpressionUtils.computeConstantExpression(expression);
    DfaValue value = history.myTopOfStack;
    if (constantExpressionValue != null && constantExpressionValue.equals(expectedValue)) {
      return new CauseItem[]{new CauseItem("it's compile-time constant which evaluates to '" + value + "'", expression)};
    }
    if (value instanceof DfaConstValue) {
      Object constValue = ((DfaConstValue)value).getValue();
      if (Objects.equals(constValue, expectedValue) && constValue instanceof Boolean) {
        return findBooleanResultCauses(expression, history, ((Boolean)constValue).booleanValue());
      }
    }
    if (value instanceof DfaVariableValue) {
      MemoryStateChange change = history.findRelation(
        (DfaVariableValue)value, rel -> rel.myRelationType == RelationType.EQ && rel.myCounterpart instanceof DfaConstValue &&
            Objects.equals(expectedValue, ((DfaConstValue)rel.myCounterpart).getValue()), false);
      if (change != null) {
        PsiExpression varSourceExpression = change.getExpression();
        Instruction instruction = change.myInstruction;
        if (instruction instanceof AssignInstruction && change.myTopOfStack == value) {
          PsiExpression rValue = ((AssignInstruction)instruction).getRExpression();
          CauseItem item = createAssignmentCause((AssignInstruction)instruction, value);
          MemoryStateChange push = change.findSubExpressionPush(rValue);
          if (push != null) {
            item.addChildren(findConstantValueCause(rValue, push, expectedValue));
          }
          return new CauseItem[]{item};
        }
        else if (varSourceExpression != null) {
          return new CauseItem[]{new CauseItem("'" + value + " == "+expectedValue+"' was established from condition", varSourceExpression)};
        }
      }
    }
    return new CauseItem[0];
  }

  @NotNull
  @Contract("_, _ -> new")
  private static CauseItem createAssignmentCause(AssignInstruction instruction, DfaValue target) {
    PsiExpression rExpression = instruction.getRExpression();
    PsiElement anchor = null;
    if (rExpression != null) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(rExpression.getParent());
      if (parent instanceof PsiAssignmentExpression) {
        anchor = ((PsiAssignmentExpression)parent).getOperationSign();
      }
      else if (parent instanceof PsiVariable) {
        ASTNode node = parent.getNode();
        if (node instanceof CompositeElement) {
          anchor = ((CompositeElement)node).findChildByRoleAsPsiElement(ChildRole.INITIALIZER_EQ);
        }
      }
      if (anchor == null) {
        anchor = rExpression;
      }
    }
    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(rExpression);
    String suffix = "";
    if (stripped instanceof PsiLiteralExpression) {
      suffix = " to '" + StringUtil.shortenTextWithEllipsis(stripped.getText(), 40, 5) + "'";
    }
    return new CauseItem("'" + target + "' was assigned" + suffix, anchor);
  }

  private static CauseItem[] findBooleanResultCauses(PsiExpression expression,
                                                     MemoryStateChange history,
                                                     boolean value) {
    if (BoolUtils.isNegation(expression)) {
      PsiExpression negated = BoolUtils.getNegated(expression);
      if (negated != null) {
        MemoryStateChange negatedPush = history.findExpressionPush(negated);
        if (negatedPush != null) {
          CauseItem cause = new CauseItem("value '" + negated.getText() + "' is always '" + !value + "'", negated);
          cause.addChildren(findConstantValueCause(negated, negatedPush, !value));
          return new CauseItem[]{cause};
        }
      }
    }
    if (expression instanceof PsiPolyadicExpression) {
      IElementType tokenType = ((PsiPolyadicExpression)expression).getOperationTokenType();
      boolean and = tokenType.equals(JavaTokenType.ANDAND);
      if ((and || tokenType.equals(JavaTokenType.OROR)) && value != and) {
        PsiExpression[] operands = ((PsiPolyadicExpression)expression).getOperands();
        for (int i = 0; i < operands.length; i++) {
          PsiExpression operand = operands[i];
          operand = PsiUtil.skipParenthesizedExprDown(operand);
          MemoryStateChange push = history.findExpressionPush(operand);
          if (push != null &&
              ((push.myInstruction instanceof ConditionalGotoInstruction &&
                ((ConditionalGotoInstruction)push.myInstruction).isTarget(value, history.myInstruction)) ||
               (push.myTopOfStack instanceof DfaConstValue &&
                Boolean.valueOf(value).equals(((DfaConstValue)push.myTopOfStack).getValue())))) {
            CauseItem cause = new CauseItem("operand #" + (i + 1) + " of " + (and ? "&&" : "||") + "-chain is " + value, operand);
            cause.addChildren(findBooleanResultCauses(operand, push, value));
            return new CauseItem[]{cause};
          }
        }
      }
    }
    if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression binOp = (PsiBinaryExpression)expression;
      RelationType relationType =
        RelationType.fromElementType(binOp.getOperationTokenType());
      if (relationType != null) {
        if (!value) {
          relationType = relationType.getNegated();
        }
        PsiExpression leftOperand = PsiUtil.skipParenthesizedExprDown(binOp.getLOperand());
        PsiExpression rightOperand = PsiUtil.skipParenthesizedExprDown(binOp.getROperand());
        MemoryStateChange leftChange = history.findExpressionPush(leftOperand);
        MemoryStateChange rightChange = history.findExpressionPush(rightOperand);
        if (leftChange != null && rightChange != null) {
          DfaValue leftValue = leftChange.myTopOfStack;
          DfaValue rightValue = rightChange.myTopOfStack;
          CauseItem[] causes = findRelationCause(relationType, leftChange, rightChange);
          if (causes.length > 0) {
            return causes;
          }
          if (leftValue == rightValue &&
              (leftValue instanceof DfaVariableValue || leftValue instanceof DfaConstValue)) {
            return new CauseItem[]{new CauseItem("comparison arguments are the same", binOp.getOperationSign())};
          }
          if (leftValue != rightValue && relationType.isInequality() &&
              leftValue instanceof DfaConstValue && rightValue instanceof DfaConstValue) {
            return new CauseItem[]{
              new CauseItem("comparison arguments are different constants", binOp.getOperationSign())};
          }
        }
      }
    }
    if (expression instanceof PsiInstanceOfExpression) {
      PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)expression;
      PsiExpression operand = instanceOfExpression.getOperand();
      MemoryStateChange operandHistory = history.findExpressionPush(operand);
      if (operandHistory != null) {
        DfaValue operandValue = operandHistory.myTopOfStack;
        if (!value) {
          Pair<MemoryStateChange, DfaNullability> nullability = operandHistory.findFact(operandValue, DfaFactType.NULLABILITY);
          if (nullability.second == DfaNullability.NULL) {
            CauseItem causeItem = new CauseItem("value '" + operand.getText() + "' is always 'null'", operand);
            causeItem.addChildren(findConstantValueCause(operand, operandHistory, null));
            return new CauseItem[]{causeItem};
          }
        }
        PsiTypeElement typeElement = instanceOfExpression.getCheckType();
        if (typeElement != null) {
          PsiType type = typeElement.getType();
          CauseItem causeItem = findTypeCause(operandHistory, type, value);
          if (causeItem != null) return new CauseItem[]{causeItem};
        }
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      return new CauseItem[]{fromCallContract(history, (PsiMethodCallExpression)expression, ContractReturnValue.returnBoolean(value))};
    }
    return new CauseItem[0];
  }

  @Nullable
  private static CauseItem findTypeCause(MemoryStateChange operandHistory, PsiType type, boolean isInstance) {
    PsiExpression operand = Objects.requireNonNull(operandHistory.getExpression());
    DfaValue operandValue = operandHistory.myTopOfStack;
    DfaPsiType wanted = operandValue.getFactory().createDfaType(type);

    Pair<MemoryStateChange, TypeConstraint> fact = operandHistory.findFact(operandValue, DfaFactType.TYPE_CONSTRAINT);
    String explanation = fact.second == null ? null : fact.second.getAssignabilityExplanation(wanted, isInstance);
    while (explanation != null) {
      MemoryStateChange causeLocation = fact.first;
      if (causeLocation == null) break;
      MemoryStateChange prevHistory = causeLocation.myPrevious;
      if (prevHistory == null) break;
      fact = prevHistory.findFact(operandValue, DfaFactType.TYPE_CONSTRAINT);
      TypeConstraint prevConstraint = fact.second == null ? TypeConstraint.empty() : fact.second;
      String prevExplanation = prevConstraint.getAssignabilityExplanation(wanted, isInstance);
      if (prevExplanation == null) {
        CauseItem causeItem = new CauseItem(explanation, operand);
        causeItem.addChildren(new CauseItem("type of '" + operand.getText() + "' is known from #ref", causeLocation));
        return causeItem;
      }
      explanation = prevExplanation;
    }
    return null;
  }

  @NotNull
  private static CauseItem[] findRelationCause(RelationType relationType,
                                               MemoryStateChange leftChange,
                                               MemoryStateChange rightChange) {
    ProgressManager.checkCanceled();
    DfaValue leftValue = leftChange.myTopOfStack;
    DfaValue rightValue = rightChange.myTopOfStack;
    Pair<MemoryStateChange, DfaNullability> leftNullability = leftChange.findFact(leftValue, DfaFactType.NULLABILITY);
    Pair<MemoryStateChange, DfaNullability> rightNullability = rightChange.findFact(rightValue, DfaFactType.NULLABILITY);
    if ((leftNullability.second == DfaNullability.NULL && rightNullability.second == DfaNullability.NOT_NULL) ||
        (rightNullability.second == DfaNullability.NULL && leftNullability.second == DfaNullability.NOT_NULL)) {
      return new CauseItem[]{findNullabilityCause(leftChange, leftNullability.first, leftNullability.second),
        findNullabilityCause(rightChange, rightNullability.first, rightNullability.second)};
    }

    Pair<MemoryStateChange, LongRangeSet> leftRange = leftChange.findFact(leftValue, DfaFactType.RANGE);
    Pair<MemoryStateChange, LongRangeSet> rightRange = rightChange.findFact(rightValue, DfaFactType.RANGE);
    if (leftRange.second != null && rightRange.second != null) {
      LongRangeSet fromRelation = rightRange.second.fromRelation(relationType.getNegated());
      if (fromRelation != null && !fromRelation.intersects(leftRange.second)) {
        return new CauseItem[]{
          findRangeCause(leftChange, leftRange.first, leftRange.second, "left operand is %s"),
          findRangeCause(rightChange, rightRange.first, rightRange.second, "right operand is %s")};
      }
    }
    if (leftValue instanceof DfaVariableValue) {
      Relation relation = new Relation(relationType, rightValue);
      MemoryStateChange change = findRelationAddedChange(leftChange, (DfaVariableValue)leftValue, relation);
      if (change != null) {
        CauseItem cause = findRelationCause(change, (DfaVariableValue)leftValue, relation, rightChange);
        if (cause != null) {
          Instruction instruction = change.myInstruction;
          if (instruction instanceof AssignInstruction) {
            PsiExpression expression = ((AssignInstruction)instruction).getRExpression();
            MemoryStateChange assignmentChange = change.findExpressionPush(expression);
            if (assignmentChange != null) {
              DfaValue target = change.myTopOfStack;
              if (target == rightValue) {
                return ArrayUtil.prepend(cause, findRelationCause(relationType, leftChange, assignmentChange));
              }
            }
          }
        }
        return new CauseItem[]{cause};
      }
    }
    if (rightValue instanceof DfaVariableValue) {
      Relation relation = new Relation(
        Objects.requireNonNull(relationType.getFlipped()), leftValue);
      MemoryStateChange change = findRelationAddedChange(rightChange, (DfaVariableValue)rightValue, relation);
      if (change != null) {
        return new CauseItem[]{findRelationCause(change, (DfaVariableValue)rightValue, relation, leftChange)};
      }
    }
    return new CauseItem[0];
  }

  private static CauseItem findRelationCause(MemoryStateChange change,
                                             DfaVariableValue value,
                                             Relation relation, MemoryStateChange counterPartChange) {
    Instruction instruction = change.myInstruction;
    String condition = value + " " + relation;
    if (instruction instanceof AssignInstruction) {
      DfaValue target = change.myTopOfStack;
      PsiExpression rValue = ((AssignInstruction)instruction).getRExpression();
      CauseItem item = createAssignmentCause((AssignInstruction)instruction, target);
      if (target == value) {
        MemoryStateChange rValuePush = change.findSubExpressionPush(rValue);
        if (rValuePush != null) {
          item.addChildren(findRelationCause(relation.myRelationType, rValuePush, counterPartChange));
        }
        return item;
      }
      if (target == relation.myCounterpart) {
        return item;
      }
    }
    PsiExpression expression = change.getExpression();
    if (expression != null) {
      if (expression instanceof PsiBinaryExpression) {
        PsiExpression lOperand = ((PsiBinaryExpression)expression).getLOperand();
        PsiExpression rOperand = ((PsiBinaryExpression)expression).getROperand();
        MemoryStateChange leftPos = change.findExpressionPush(lOperand);
        MemoryStateChange rightPos = change.findExpressionPush(rOperand);
        if (leftPos != null && rightPos != null) {
          DfaValue leftValue = leftPos.myTopOfStack;
          DfaValue rightValue = rightPos.myTopOfStack;
          if (leftValue == value && rightValue == relation.myCounterpart ||
              rightValue == value && leftValue == relation.myCounterpart) {
            return new CauseItem(new CustomDfaProblemType("condition '" + condition + "' was checked before"), expression);
          }
        }
      }
      return new CauseItem(new CustomDfaProblemType("result of '" + condition + "' is known from #ref"), expression);
    }
    return null;
  }

  private static CauseItem findNullabilityCause(MemoryStateChange factUse, MemoryStateChange factDef, DfaNullability nullability) {
    PsiExpression expression = factUse.getExpression();
    if (expression instanceof PsiTypeCastExpression) {
      MemoryStateChange operandPush = factUse.findSubExpressionPush(((PsiTypeCastExpression)expression).getOperand());
      if (operandPush != null) {
        return findNullabilityCause(operandPush, factDef, nullability);
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      PsiMethod method = call.resolveMethod();
      CauseItem causeItem = fromMemberNullability(nullability, method, "method", call.getMethodExpression().getReferenceNameElement());
      if (causeItem != null) return causeItem;
      switch (nullability) {
        case NULL:
        case NULLABLE:
          return fromCallContract(factUse, call, ContractReturnValue.returnNull());
        case NOT_NULL:
          return fromCallContract(factUse, call, ContractReturnValue.returnNotNull());
        default:
      }
    }
    if (expression instanceof PsiReferenceExpression) {
      PsiVariable variable = ObjectUtils.tryCast(((PsiReferenceExpression)expression).resolve(), PsiVariable.class);
      if (variable != null) {
        String memberTitle = variable instanceof PsiField ? "field" :
                             variable instanceof PsiParameter ? "parameter" :
                             "variable";
        CauseItem causeItem = fromMemberNullability(nullability, variable, memberTitle, expression);
        if (causeItem != null) {
          return causeItem;
        }
      }
    }
    if (nullability == DfaNullability.NOT_NULL) {
      String explanation = getObviouslyNonNullExplanation(expression);
      if (explanation != null) {
        return new CauseItem("expression cannot be null as it's " + explanation, expression);
      }
      if (factDef != null) {
        if (factDef.myInstruction instanceof CheckNotNullInstruction) {
          PsiExpression dereferenced = ((CheckNotNullInstruction)factDef.myInstruction).getProblem().getDereferencedExpression();
          String text = dereferenced == null ? factUse.myTopOfStack.toString() : dereferenced.getText();
          return new CauseItem("'" + text + "' was dereferenced", dereferenced);
        }
        if (factDef.myInstruction instanceof InstanceofInstruction) {
          PsiExpression operand = ((InstanceofInstruction)factDef.myInstruction).getExpression();
          return new CauseItem("the 'instanceof' check implies non-nullity", operand);
        }
      }
    }
    if (factDef != null && expression != null) {
      DfaValue value = factUse.myTopOfStack;
      if (factDef.myInstruction instanceof AssignInstruction && factDef.myTopOfStack == value) {
        PsiExpression rExpression = ((AssignInstruction)factDef.myInstruction).getRExpression();
        if (rExpression != null) {
          MemoryStateChange rValuePush = factDef.findSubExpressionPush(rExpression);
          if (rValuePush != null) {
            CauseItem assignmentItem = createAssignmentCause((AssignInstruction)factDef.myInstruction, value);
            Pair<MemoryStateChange, DfaNullability> rValueFact = rValuePush.findFact(rValuePush.myTopOfStack, DfaFactType.NULLABILITY);
            assignmentItem.addChildren(findNullabilityCause(rValuePush, rValueFact.first, nullability));
            return assignmentItem;
          }
        }
      }
      PsiExpression defExpression = factDef.getExpression();
      if (defExpression != null) {
        return new CauseItem("'" + expression.getText() + "' is known to be '" + nullability.getPresentationName() + "' from #ref",
                             defExpression);
      }
    }
    return null;
  }

  private static CauseItem fromMemberNullability(DfaNullability nullability,
                                                 PsiModifierListOwner owner,
                                                 String memberName, PsiElement anchor) {
    if (owner != null) {
      NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(owner.getProject()).findEffectiveNullabilityInfo(owner);
      if (info != null && DfaNullability.fromNullability(info.getNullability()) == nullability) {
        String message;
        String name = ((PsiNamedElement)owner).getName();
        if (info.isInferred()) {
          if (owner instanceof PsiParameter &&
              anchor instanceof PsiReferenceExpression &&
              ((PsiReferenceExpression)anchor).isReferenceTo(owner)) {
            // Do not use inference inside method itself
            return null;
          }
          message = memberName + " '" + name + "' was inferred to be '" + nullability.getPresentationName() + "'";
        }
        else if (info.isExternal()) {
          message = memberName + " '" + name + "' is externally annotated as '" + nullability.getPresentationName() + "'";
        }
        else if (info.isContainer()) {
          PsiAnnotationOwner annoOwner = info.getAnnotation().getOwner();
          String details = "container annotation";
          if (annoOwner instanceof PsiModifierList) {
            PsiElement parent = ((PsiModifierList)annoOwner).getParent();
            if (parent instanceof PsiClass) {
              PsiClass aClass = (PsiClass)parent;
              details = "annotation from class " + aClass.getName();
              if ("package-info".equals(aClass.getName())) {
                PsiFile file = aClass.getContainingFile();
                if (file instanceof PsiJavaFile) {
                  details = "annotation from package " + ((PsiJavaFile)file).getPackageName();
                }
              }
            }
          }
          if (annoOwner instanceof PsiNamedElement) {
            details = " from " + ((PsiNamedElement)annoOwner).getName();
          }
          message =
            memberName + " '" + name + "' inherits " + details + ", thus '" + nullability.getPresentationName() + "'";
        }
        else {
          message = memberName + " '" + name + "' is annotated as '" + nullability.getPresentationName() + "'";
        }
        if (info.getAnnotation().getContainingFile() == anchor.getContainingFile()) {
          anchor = info.getAnnotation();
        } else if (owner.getContainingFile() == anchor.getContainingFile()) {
          anchor = owner.getNavigationElement();
          if (anchor instanceof PsiNameIdentifierOwner) {
            anchor = ((PsiNameIdentifierOwner)anchor).getNameIdentifier();
          }
        }
        return new CauseItem(message, anchor);
      }
    }
    return null;
  }

  private static CauseItem fromCallContract(MemoryStateChange history, PsiCallExpression call, ContractReturnValue contractReturnValue) {
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    List<? extends MethodContract> contracts =
      ContainerUtil.filter(JavaMethodContractUtil.getMethodCallContracts(method, call),
                           mc -> contractReturnValue.isSuperValueOf(mc.getReturnValue()));
    boolean explicit = JavaMethodContractUtil.hasExplicitContractAnnotation(method);
    if (call instanceof PsiMethodCallExpression) {
      PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)call).getMethodExpression();
      String name = methodExpression.getReferenceName();
      for (MethodContract contract : contracts) {
        if (contract.isTrivial()) {
          return new CauseItem("according to " + (explicit ? "contract" : "inferred contract") +
                               ", method '" + name + "' always returns '" + contract.getReturnValue() + "' value",
                               methodExpression.getReferenceNameElement());
        }
      }
    }
    return null;
  }

  private static CauseItem findRangeCause(MemoryStateChange factUse,
                                          MemoryStateChange factDef,
                                          LongRangeSet range,
                                          String template) {
    DfaValue value = factUse.myTopOfStack;
    if (value instanceof DfaVariableValue) {
      VariableDescriptor descriptor = ((DfaVariableValue)value).getDescriptor();
      if (descriptor instanceof SpecialField && range.equals(LongRangeSet.indexRange())) {
        switch (((SpecialField)descriptor)) {
          case ARRAY_LENGTH:
            return new CauseItem("array length is always non-negative", factUse);
          case STRING_LENGTH:
            return new CauseItem("string length is always non-negative", factUse);
          case COLLECTION_SIZE:
            return new CauseItem("collection size is always non-negative", factUse);
          default:
        }
      }
    }
    PsiExpression expression = factUse.getExpression();
    if (expression != null) {
      if (expression instanceof PsiLiteralExpression) {
        return null; // Literal range is quite evident
      }
      if (expression instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
        PsiMethod method = call.resolveMethod();
        if (method != null) {
          LongRangeSet fromAnnotation = LongRangeSet.fromPsiElement(method);
          if (fromAnnotation.equals(range)) {
            return new CauseItem("the range of '" + method.getName() + "' is specified by annotation as " + range,
                                 call.getMethodExpression().getReferenceNameElement());
          }
        }
      }
      if (expression instanceof PsiBinaryExpression &&
          (PsiType.LONG.equals(expression.getType()) || PsiType.INT.equals(expression.getType()))) {
        boolean isLong = PsiType.LONG.equals(expression.getType());
        PsiBinaryExpression binOp = (PsiBinaryExpression)expression;
        PsiExpression left = PsiUtil.skipParenthesizedExprDown(binOp.getLOperand());
        PsiExpression right = PsiUtil.skipParenthesizedExprDown(binOp.getROperand());
        MemoryStateChange leftPush = factUse.findExpressionPush(left);
        MemoryStateChange rightPush = factUse.findExpressionPush(right);
        if (leftPush != null && rightPush != null) {
          DfaValue leftValue = leftPush.myTopOfStack;
          DfaValue rightValue = rightPush.myTopOfStack;
          Pair<MemoryStateChange, LongRangeSet> leftSet = leftPush.findFact(leftValue, DfaFactType.RANGE);
          Pair<MemoryStateChange, LongRangeSet> rightSet = rightPush.findFact(rightValue, DfaFactType.RANGE);
          LongRangeSet fromType = Objects.requireNonNull(LongRangeSet.fromType(expression.getType()));
          if (leftSet.second == null) {
            leftSet = Pair.create(null, fromType);
          }
          if (rightSet.second == null) {
            rightSet = Pair.create(null, fromType);
          }
          LongRangeSet result = leftSet.second.binOpFromToken(binOp.getOperationTokenType(), rightSet.second, isLong);
          if (range.equals(result)) {
            CauseItem cause = new CauseItem("result of '" + binOp.getOperationSign().getText() +
                                            "' is " + range.getPresentationText(expression.getType()), factUse);
            CauseItem leftCause = null, rightCause = null;
            if (!leftSet.second.equals(fromType)) {
              leftCause = findRangeCause(leftPush, leftSet.first, leftSet.second, "left operand is %s");
            }
            if (!rightSet.second.equals(fromType)) {
              rightCause = findRangeCause(rightPush, rightSet.first, rightSet.second, "right operand is %s");
            }
            cause.addChildren(leftCause, rightCause);
            return cause;
          }
        }
      }
    }
    String rangeText = range.getPresentationText(expression != null ? expression.getType() : null);
    CauseItem item = new CauseItem(String.format(template, rangeText), factUse);
    if (factDef != null) {
      if (factDef.myInstruction instanceof AssignInstruction && factDef.myTopOfStack == value) {
        PsiExpression rExpression = ((AssignInstruction)factDef.myInstruction).getRExpression();
        if (rExpression != null) {
          MemoryStateChange rValuePush = factDef.findSubExpressionPush(rExpression);
          if (rValuePush != null) {
            CauseItem assignmentItem = createAssignmentCause((AssignInstruction)factDef.myInstruction, value);
            Pair<MemoryStateChange, LongRangeSet> rValueFact = rValuePush.findFact(rValuePush.myTopOfStack, DfaFactType.RANGE);
            assignmentItem.addChildren(findRangeCause(rValuePush, rValueFact.first, range, "Value is %s"));
            item.addChildren(assignmentItem);
            return item;
          }
        }
      }
      PsiExpression defExpression = factDef.getExpression();
      if (defExpression != null) {
        item.addChildren(new CauseItem("range is known from #ref", defExpression));
      }
    }
    return item;
  }

  @Nullable
  public static String getObviouslyNonNullExplanation(PsiExpression arg) {
    if (arg == null || ExpressionUtils.isNullLiteral(arg)) return null;
    if (arg instanceof PsiNewExpression) return "newly created object";
    if (arg instanceof PsiLiteralExpression) return "literal";
    if (arg.getType() instanceof PsiPrimitiveType) return "a value of primitive type '" + arg.getType().getCanonicalText() + "'";
    if (arg instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)arg).getOperationTokenType() == JavaTokenType.PLUS) {
      return "concatenation";
    }
    if (arg instanceof PsiThisExpression) return "'this' object";
    return null;
  }

  private static MemoryStateChange findRelationAddedChange(MemoryStateChange history, DfaVariableValue var, Relation relation) {
    List<RelationType> subRelations;
    switch (relation.myRelationType) {
      case NE:
        if (relation.myCounterpart instanceof DfaConstValue) {
          return history.findRelation(var, rel -> rel.equals(relation) ||
                                                  rel.myRelationType == RelationType.EQ && rel.myCounterpart instanceof DfaConstValue,
                                      true);
        }
        subRelations = Arrays.asList(RelationType.NE, RelationType.GT, RelationType.LT);
        break;
      case LE:
        subRelations = Arrays.asList(RelationType.EQ, RelationType.LT);
        break;
      case GE:
        subRelations = Arrays.asList(RelationType.EQ, RelationType.GT);
        break;
      default:
        subRelations = Collections.singletonList(relation.myRelationType);
    }
    return history.findRelation(var, rel -> rel.myCounterpart == relation.myCounterpart && subRelations.contains(rel.myRelationType), true);
  }
}
