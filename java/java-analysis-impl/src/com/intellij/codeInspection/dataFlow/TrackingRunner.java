// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.TrackingDfaMemoryState.MemoryStateChange;
import com.intellij.codeInspection.dataFlow.TrackingDfaMemoryState.Relation;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

@SuppressWarnings("SuspiciousNameCombination")
public class TrackingRunner extends StandardDataFlowRunner {
  private final List<MemoryStateChange> myHistoryForContext = new ArrayList<>();
  private final PsiExpression myExpression;

  public TrackingRunner(boolean unknownMembersAreNullable, @Nullable PsiElement context, PsiExpression expression) {
    super(unknownMembersAreNullable, context);
    myExpression = expression;
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
      ((TrackingDfaMemoryState)state.getMemoryState()).recordChange(instruction, memState);
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
    StandardInstructionVisitor visitor = new StandardInstructionVisitor();
    RunnerResult result = runner.analyzeMethodRecursively(body, visitor, ignoreAssertions);
    if (result != RunnerResult.OK) return Collections.emptyList();
    List<CauseItem> results = new ArrayList<>();
    for (MemoryStateChange history : runner.myHistoryForContext) {
      CauseItem root = findCauseChain(expression, history, type);
      if (!results.contains(root)) {
        results.add(root);
      }
    }
    return results;
  }

  public abstract static class DfaProblemType {
    public abstract String toString();
  }

  public static class CauseItem {
    final @NotNull List<CauseItem> myChildren;
    final @NotNull DfaProblemType myProblem;
    final @Nullable PsiElement myTarget;

    CauseItem(@NotNull String problem, @Nullable PsiElement target) {
      this(new CustomDfaProblemType(problem), target);
    }

    CauseItem(@NotNull DfaProblemType problem, @Nullable PsiElement target) {
      myChildren = new ArrayList<>();
      myProblem = problem;
      myTarget = target;
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
             myProblem.toString().equals(item.myProblem.toString()) &&
             Objects.equals(myTarget, item.myTarget);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myChildren, myProblem, myTarget);
    }

    public String dump(Document doc) {
      return dump(doc, 0);
    }

    private String dump(Document doc, int indent) {
      return StringUtil.repeat("  ", indent) + render(doc) + (myTarget == null ? "" : " (" + myTarget.getText() + ")") + "\n" +
             StreamEx.of(myChildren).map(child -> child.dump(doc, indent + 1)).joining();
    }

    public Stream<CauseItem> children() {
      return StreamEx.of(myChildren);
    }

    @Nullable
    public PsiElement getTarget() {
      return myTarget;
    }

    public String render(Document doc) {
      if (myTarget != null) {
        String cause = myProblem.toString();
        if (cause.endsWith("#ref")) {
          int offset = myTarget.getTextRange().getStartOffset();
          int number = doc.getLineNumber(offset);
          return cause.replaceFirst("#ref$", "line #" + (number + 1));
        }
      }
      return toString();
    }

    @Override
    public String toString() {
      return myProblem.toString().replaceFirst("#ref$", "here");
    }
  }

  public static class ValueDfaProblemType extends DfaProblemType {
    final Object myValue;

    public ValueDfaProblemType(Object value) {
      myValue = value;
    }

    @Override
    public String toString() {
      return "Value is always " + myValue;
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
            Cause for possible NPE
            Cause for AIOOBE
            Cause for "Contract always fails"
            Cause for possible CCE
            Cause for "modifying an immutable collection"
            Cause for "Collection is always empty" (separate inspection now)
  TODO: 2. Describe causes in more cases:
            Warning caused by contract
            Warning caused by CustomMethodHandler
            Warning caused by mismatched type constraints
  TODO: 3. Check how it works with 
            Inliners (notably: Stream API)
            Ternary operators
  TODO: 4. Check for possible performance disasters (likely on some code patterns current algo might blow up)
  TODO: 5. Problem when interesting state doesn't reach the current condition, need to do something with this    
   */
  @NotNull
  private static CauseItem findCauseChain(PsiExpression expression, MemoryStateChange history, DfaProblemType type) {
    CauseItem root = new CauseItem(type, expression);
    if (history.getExpression() != expression) return root;
    if (type instanceof ValueDfaProblemType) {
      Object expectedValue = ((ValueDfaProblemType)type).myValue;
      CauseItem[] causes = findConstantValueCause(expression, history, expectedValue);
      root.addChildren(causes);
    }
    return root;
  }

  @NotNull
  private static CauseItem[] findConstantValueCause(PsiExpression expression, MemoryStateChange history, Object expectedValue) {
    Object constantExpressionValue = ExpressionUtils.computeConstantExpression(expression);
    DfaValue value = history.myTopOfStack;
    if (constantExpressionValue != null && constantExpressionValue.equals(expectedValue)) {
      return new CauseItem[]{new CauseItem("It's compile-time constant which evaluates to '" + value + "'", expression)};
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
            Objects.equals(expectedValue, ((DfaConstValue)rel.myCounterpart).getValue()));
      if (change != null) {
        PsiExpression varSourceExpression = change.getExpression();
        Instruction instruction = change.myInstruction;
        if (instruction instanceof AssignInstruction && change.myTopOfStack == value) {
          PsiExpression rValue = ((AssignInstruction)instruction).getRExpression();
          CauseItem item = new CauseItem("'" + value + "' was assigned", rValue);
          MemoryStateChange push = change.findExpressionPush(rValue);
          if (push != null) {
            item.addChildren(findConstantValueCause(rValue, push, expectedValue));
          }
          return new CauseItem[]{item};
        }
        else if (expectedValue instanceof Boolean && varSourceExpression != null) {
          return new CauseItem[]{new CauseItem("'" + value + " == "+expectedValue+"' was established from condition", varSourceExpression)};
        }
      }
    }
    return new CauseItem[0];
  }

  private static CauseItem[] findBooleanResultCauses(PsiExpression expression,
                                                     MemoryStateChange history,
                                                     boolean value) {
    if (BoolUtils.isNegation(expression)) {
      PsiExpression negated = BoolUtils.getNegated(expression);
      if (negated != null) {
        MemoryStateChange negatedPush = history.findExpressionPush(negated);
        if (negatedPush != null) {
          CauseItem cause = new CauseItem("Value '" + negated.getText() + "' is always '" + !value + "'", negated);
          cause.addChildren(findConstantValueCause(negated, negatedPush, !value));
          return new CauseItem[]{cause};
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
        PsiExpression leftOperand = binOp.getLOperand();
        PsiExpression rightOperand = binOp.getROperand();
        MemoryStateChange leftChange = history.findExpressionPush(leftOperand);
        MemoryStateChange rightChange = history.findExpressionPush(rightOperand);
        if (leftChange != null && rightChange != null) {
          DfaValue leftValue = leftChange.myTopOfStack;
          DfaValue rightValue = rightChange.myTopOfStack;
          if (leftValue == rightValue &&
              (leftValue instanceof DfaVariableValue || leftValue instanceof DfaConstValue)) {
            return new CauseItem[]{new CauseItem("Comparison arguments are the same", binOp.getOperationSign())};
          }
          if (leftValue != rightValue && relationType.isInequality() &&
              leftValue instanceof DfaConstValue && rightValue instanceof DfaConstValue) {
            return new CauseItem[]{
              new CauseItem("Comparison arguments are different constants", binOp.getOperationSign())};
          }
          return findRelationCause(relationType, leftChange, rightChange);
        }
      }
    }
    return new CauseItem[0];
  }

  @NotNull
  private static CauseItem[] findRelationCause(RelationType relationType,
                                               MemoryStateChange leftChange,
                                               MemoryStateChange rightChange) {
    DfaValue leftValue = leftChange.myTopOfStack;
    DfaValue rightValue = rightChange.myTopOfStack;
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
          findRangeCause(leftChange, leftRange.first, leftRange.second, "Left operand range is %s"),
          findRangeCause(rightChange, rightRange.first, rightRange.second, "Right operand range is %s")};
      }
    }
    return new CauseItem[0];
  }

  private static CauseItem findRelationCause(MemoryStateChange change,
                                             DfaVariableValue value,
                                             Relation relation, MemoryStateChange counterPartChange) {
    Instruction instruction = change.myInstruction;
    String condition = value + " " + relation;
    if (relation.myCounterpart instanceof DfaConstValue &&
        ((DfaConstValue)relation.myCounterpart).getValue() == null && relation.myRelationType == RelationType.NE) {
      if (instruction instanceof CheckNotNullInstruction) {
        PsiExpression expression = ((CheckNotNullInstruction)instruction).getProblem().getDereferencedExpression();
        String text = expression == null ? value.toString() : expression.getText();
        return new CauseItem("'" + text + "' was dereferenced", expression);
      }
      if (instruction instanceof InstanceofInstruction) {
        PsiExpression expression = ((InstanceofInstruction)instruction).getExpression();
        return new CauseItem("The 'instanceof' check implies non-nullity", expression);
      }
    }
    if (instruction instanceof AssignInstruction) {
      DfaValue target = change.myTopOfStack;
      PsiExpression rValue = ((AssignInstruction)instruction).getRExpression();
      if (target == value) {
        CauseItem item = new CauseItem("'" + target + "' was assigned", rValue);
        MemoryStateChange rValuePush = change.findExpressionPush(rValue);
        if (rValuePush != null) {
          item.addChildren(findRelationCause(relation.myRelationType, rValuePush, counterPartChange));
        }
        return item;
      }
      if (target == relation.myCounterpart) {
        return new CauseItem("'" + target + "' was assigned", rValue);
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
            return new CauseItem(new CustomDfaProblemType(condition + " was checked before"), expression);
          }
        }
      }
      return new CauseItem(new CustomDfaProblemType(condition + " is known from #ref"), expression);
    }
    return null;
  }

  private static CauseItem findNullabilityCause(MemoryStateChange factUse, MemoryStateChange factDef, DfaNullability nullability) {
    PsiExpression expression = factUse.getExpression();
    if (factDef != null && expression != null) {
      PsiExpression defExpression = factDef.getExpression();
      if (defExpression != null) {
        return new CauseItem(
          new CustomDfaProblemType(expression.getText() + " is known to be '" + nullability.getPresentationName() + "' from #ref"),
          defExpression);
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      PsiMethod method = call.resolveMethod();
      return fromMemberNullability(nullability, method, "Method", call.getMethodExpression().getReferenceNameElement());
    }
    if (expression instanceof PsiReferenceExpression) {
      PsiVariable variable = ObjectUtils.tryCast(((PsiReferenceExpression)expression).resolve(), PsiVariable.class);
      if (variable instanceof PsiField) {
        return fromMemberNullability(nullability, variable, "Field", ((PsiReferenceExpression)expression).getReferenceNameElement());
      }
      if (variable instanceof PsiParameter) {
        return fromMemberNullability(nullability, variable, "Parameter", ((PsiReferenceExpression)expression).getReferenceNameElement());
      }
      if (variable != null) {
        return fromMemberNullability(nullability, variable, "Variable", ((PsiReferenceExpression)expression).getReferenceNameElement());
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
          message = memberName + " '" + name + "' was inferred to be '" + nullability.getPresentationName() + "'";
        }
        else if (info.isExternal()) {
          message = memberName + " '" + name + "' is externally annotated as '" + nullability.getPresentationName() + "'";
        }
        else if (info.isContainer()) {
          message = memberName + " '" + name + "' inherits container annotation, thus '" + nullability.getPresentationName() + "'";
        }
        else {
          message = memberName + " '" + name + "' is annotated as '" + nullability.getPresentationName() + "'";
        }
        if (owner.getContainingFile() == anchor.getContainingFile()) {
          anchor = owner.getNavigationElement();
        }
        return new CauseItem(message, anchor);
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
            return new CauseItem("Array length is always non-negative", factUse);
          case STRING_LENGTH:
            return new CauseItem("String length is always non-negative", factUse);
          case COLLECTION_SIZE:
            return new CauseItem("Collection size is always non-negative", factUse);
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
            return new CauseItem("The range of '" + method.getName() + "' is specified by annotation as " + range,
                                 call.getMethodExpression().getReferenceNameElement());
          }
        }
      }
      if (expression instanceof PsiBinaryExpression &&
          (PsiType.LONG.equals(expression.getType()) || PsiType.INT.equals(expression.getType()))) {
        boolean isLong = PsiType.LONG.equals(expression.getType());
        PsiBinaryExpression binOp = (PsiBinaryExpression)expression;
        PsiExpression left = binOp.getLOperand();
        PsiExpression right = binOp.getROperand();
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
            CauseItem cause = new CauseItem("Range of '" + binOp.getOperationSign().getText() + "' result is " + range, factUse);
            CauseItem leftCause = null, rightCause = null;
            if (!leftSet.second.equals(fromType)) {
              leftCause = findRangeCause(leftPush, leftSet.first, leftSet.second, "Left operand range is %s");
            }
            if (!rightSet.second.equals(fromType)) {
              rightCause = findRangeCause(rightPush, rightSet.first, rightSet.second, "Right operand range is %s");
            }
            cause.addChildren(leftCause, rightCause);
            return cause;
          }
        }
      }
    }
    CauseItem item = new CauseItem(String.format(template, range.toString()), factUse);
    if (factDef != null) {
      PsiExpression defExpression = factDef.getExpression();
      if (defExpression != null) {
        item.addChildren(new CauseItem("Range is known from #ref", defExpression));
      }
    }
    return item;
  }

  private static MemoryStateChange findRelationAddedChange(MemoryStateChange history, DfaVariableValue var, Relation relation) {
    List<Relation> subRelations;
    switch (relation.myRelationType) {
      case NE:
        subRelations = Arrays.asList(relation, new Relation(RelationType.GT, relation.myCounterpart),
                                     new Relation(RelationType.LT, relation.myCounterpart));
        break;
      case LE:
        subRelations = Arrays.asList(new Relation(RelationType.EQ, relation.myCounterpart),
                                     new Relation(RelationType.LT, relation.myCounterpart));
        break;
      case GE:
        subRelations = Arrays.asList(new Relation(RelationType.EQ, relation.myCounterpart),
                                     new Relation(RelationType.GT, relation.myCounterpart));
        break;
      default:
        subRelations = Collections.singletonList(relation);
    }
    return history.findRelation(var, subRelations::contains);
  }
}
