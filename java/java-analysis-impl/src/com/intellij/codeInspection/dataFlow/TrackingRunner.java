// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.TrackingDfaMemoryState.FactDefinition;
import com.intellij.codeInspection.dataFlow.TrackingDfaMemoryState.FactExtractor;
import com.intellij.codeInspection.dataFlow.TrackingDfaMemoryState.MemoryStateChange;
import com.intellij.codeInspection.dataFlow.TrackingDfaMemoryState.Relation;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.value.*;
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
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static com.intellij.codeInspection.dataFlow.DfaUtil.hasImplicitImpureSuperCall;

@SuppressWarnings("SuspiciousNameCombination")
public class TrackingRunner extends DataFlowRunner {
  private MemoryStateChange myHistoryForContext = null;
  private final PsiExpression myExpression;
  private final List<DfaInstructionState> afterStates = new ArrayList<>();
  private final List<TrackingDfaMemoryState> killedStates = new ArrayList<>();

  private TrackingRunner(@NotNull PsiElement context,
                         PsiExpression expression,
                         boolean unknownMembersAreNullable,
                         boolean ignoreAssertions) {
    super(context.getProject(), context, unknownMembersAreNullable, ThreeState.fromBoolean(ignoreAssertions));
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

  @Override
  protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull InstructionVisitor visitor, @NotNull DfaInstructionState instructionState) {
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
      ExpressionPushingInstruction<?> pushing = (ExpressionPushingInstruction<?>)instruction;
      if (pushing.getExpression() == myExpression && pushing.getExpressionRange() == null) {
        for (DfaInstructionState state : states) {
          MemoryStateChange history = ((TrackingDfaMemoryState)state.getMemoryState()).getHistory();
          myHistoryForContext = myHistoryForContext == null ? history : myHistoryForContext.merge(history);
        }
      }
    }
    return states;
  }

  @Nullable
  public static CauseItem findProblemCause(boolean unknownAreNullables,
                                           boolean ignoreAssertions,
                                           PsiExpression expression,
                                           DfaProblemType type) {
    PsiElement body = DfaUtil.getDataflowContext(expression);
    if (body == null) return null;
    TrackingRunner runner = new TrackingRunner(body, expression, unknownAreNullables, ignoreAssertions);
    if (!runner.analyze(expression, body)) return null;
    return runner.findProblemCause(expression, type);
  }

  private boolean analyze(PsiExpression expression, PsiElement body) {
    List<DfaMemoryState> endOfInitializerStates = new ArrayList<>();
    StandardInstructionVisitor visitor = new StandardInstructionVisitor(true) {
      @Override
      public DfaInstructionState[] visitEndOfInitializer(EndOfInitializerInstruction instruction,
                                                         DataFlowRunner runner,
                                                         DfaMemoryState state) {
        if (!instruction.isStatic()) {
          endOfInitializerStates.add(state.createCopy());
        }
        return super.visitEndOfInitializer(instruction, runner, state);
      }
    };
    RunnerResult result = analyzeMethodRecursively(body, visitor);
    if (result != RunnerResult.OK) return false;
    if (body instanceof PsiClass) {
      PsiMethod ctor = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (ctor != null && ctor.isConstructor()) {
        List<DfaMemoryState> initialStates;
        PsiCodeBlock ctorBody = ctor.getBody();
        if (ctorBody != null) {
          PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(ctor);
          if (JavaPsiConstructorUtil.isChainedConstructorCall(call) ||
              (call == null && hasImplicitImpureSuperCall((PsiClass)body, ctor))) {
            initialStates = Collections.singletonList(createMemoryState());
          }
          else {
            initialStates = StreamEx.of(endOfInitializerStates).map(DfaMemoryState::createCopy).toList();
          }
          return analyzeBlockRecursively(ctorBody, initialStates, visitor) == RunnerResult.OK;
        }
      }
    }
    return true;
  }

  /*
  TODO: 1. Find causes of other warnings:  
            Cause for AIOOBE
            Cause for "modifying an immutable collection"
            Cause for "Collection is always empty" (separate inspection now)
  TODO: 2. Describe causes in more cases:
            Warning caused by complex contracts
            Warning caused by CustomMethodHandler
            Warning caused by polyadic math
            Warning caused by unary minus
            Warning caused by string concatenation
            Warning caused by java.lang.Void nullability
            Warning caused by getClass() equality
            Warning caused by inliners
  TODO: 3. Check how it works with:
            Boxed numbers
   */
  @Nullable
  private CauseItem findProblemCause(PsiExpression expression, DfaProblemType type) {
    if (myHistoryForContext == null) return null;
    CauseItem cause = null;
    do {
      CauseItem item = new CauseItem(type, expression);
      MemoryStateChange history = myHistoryForContext.getNonMerge();
      if (history.getExpression() == expression) {
        item.addChildren(type.findCauses(this, expression, history));
      }
      if (cause == null) {
        cause = item;
      }
      else {
        cause = cause.merge(item);
        if (cause == null) return null;
      }
    }
    while (myHistoryForContext.advance());
    return cause;
  }

  public abstract static class DfaProblemType {
    public abstract String toString();

    CauseItem[] findCauses(TrackingRunner runner, PsiExpression expression, MemoryStateChange history) {
      return new CauseItem[0];
    }

    @Nullable
    DfaProblemType tryMerge(DfaProblemType other) {
      return this.toString().equals(other.toString()) ? this : null; 
    }
  }

  public static class CauseItem {
    final @NotNull List<CauseItem> myChildren;
    final @NotNull DfaProblemType myProblem;
    final @Nullable SmartPsiFileRange myTarget;

    private CauseItem(@NotNull List<CauseItem> children, @NotNull DfaProblemType problem, @Nullable SmartPsiFileRange target) {
      myChildren = children;
      myProblem = problem;
      myTarget = target;
    }
    
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
      if (Objects.equals(this.myTarget, other.myTarget)) {
        if (myChildren.equals(other.myChildren)) {
          DfaProblemType mergedProblem = myProblem.tryMerge(other.myProblem);
          if (mergedProblem != null) {
            return new CauseItem(myChildren, mergedProblem, myTarget);
          }
        }
        if (getProblemName().equals(other.getProblemName())) {
          if (tryMergeChildren(other.myChildren)) return this;
          if (other.tryMergeChildren(this.myChildren)) return other;
        }
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
      List<CauseItem> mergeChildren = mergePoint.myChildren;
      for (CauseItem child : children) {
        if (!mergeChildren.contains(child)) {
          boolean merged = false;
          for (int i = 0; i < mergeChildren.size(); i++) {
            CauseItem mergeChild = mergeChildren.get(i);
            CauseItem result = mergeChild.merge(child);
            if (result != null) {
              mergeChildren.set(i, result);
              merged = true;
              break;
            }
          }
          if (!merged) {
            mergeChildren.add(child);
          }
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
    public CauseItem[] findCauses(TrackingRunner runner, PsiExpression expression, MemoryStateChange history) {
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
    public CauseItem[] findCauses(TrackingRunner runner, PsiExpression expression, MemoryStateChange history) {
      FactDefinition<DfaNullability> nullability = history.findFact(history.myTopOfStack, FactExtractor.nullability());
      if (nullability.myFact == DfaNullability.NULLABLE || nullability.myFact == DfaNullability.NULL) {
        return new CauseItem[]{runner.findNullabilityCause(history, nullability.myFact)};
      }
      return new CauseItem[0];
    }

    public String toString() {
      return "may be null";
    }
  }

  public static class FailingCallDfaProblemType extends DfaProblemType {
    @Override
    CauseItem[] findCauses(TrackingRunner runner, PsiExpression expression, MemoryStateChange history) {
      if (expression instanceof PsiCallExpression) {
        return new CauseItem[]{runner.fromCallContract(history, (PsiCallExpression)expression, ContractReturnValue.fail())};
      }
      return super.findCauses(runner, expression, history);
    }

    @Override
    public String toString() {
      return "call always fails";
    }
  }
  

  static class PossibleExecutionDfaProblemType extends DfaProblemType {
    boolean myComplete = true;

    @Override
    public String toString() {
      return myComplete ? "one of the following happens:" : "an execution might exist where:";
    }
  }

  static class RangeDfaProblemType extends DfaProblemType {
    final @NotNull String myTemplate;
    final @NotNull LongRangeSet myRangeSet;
    final @Nullable PsiPrimitiveType myType;

    RangeDfaProblemType(@NotNull String template, @NotNull LongRangeSet set, @Nullable PsiPrimitiveType type) {
      myTemplate = template;
      myRangeSet = set;
      myType = type;
    }

    @Nullable
    @Override
    DfaProblemType tryMerge(DfaProblemType other) {
      if (other instanceof RangeDfaProblemType) {
        RangeDfaProblemType rangeProblem = (RangeDfaProblemType)other;
        if (myTemplate.equals(rangeProblem.myTemplate) && Objects.equals(myType, rangeProblem.myType)) {
          return new RangeDfaProblemType(myTemplate, myRangeSet.unite(((RangeDfaProblemType)other).myRangeSet), myType);
        }
      }
      return super.tryMerge(other);
    }

    @Override
    public String toString() {
      return String.format(myTemplate, myRangeSet.getPresentationText(myType));
    }
  }
  

  public static class ValueDfaProblemType extends DfaProblemType {
    final Object myValue;

    public ValueDfaProblemType(Object value) {
      myValue = value;
    }

    @Override
    public CauseItem[] findCauses(TrackingRunner runner, PsiExpression expression, MemoryStateChange history) {
      return runner.findConstantValueCause(expression, history, myValue);
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

  private CauseItem @NotNull [] findConstantValueCause(PsiExpression expression, MemoryStateChange history, Object expectedValue) {
    if (expression instanceof PsiLiteralExpression) return new CauseItem[0];
    Object constantExpressionValue = ExpressionUtils.computeConstantExpression(expression);
    DfaValue value = history.myTopOfStack;
    if (constantExpressionValue != null && constantExpressionValue.equals(expectedValue)) {
      return new CauseItem[]{new CauseItem("it's compile-time constant which evaluates to '" + value + "'", expression)};
    }
    if (value.getDfType() instanceof DfConstantType) {
      Object constValue = ((DfConstantType<?>)value.getDfType()).getValue();
      if (Objects.equals(constValue, expectedValue) && constValue instanceof Boolean) {
        return findBooleanResultCauses(expression, history, ((Boolean)constValue).booleanValue());
      }
    }
    if (value instanceof DfaVariableValue) {
      MemoryStateChange change = history.findRelation(
        (DfaVariableValue)value, rel -> rel.myRelationType == RelationType.EQ &&
            DfConstantType.isConst(rel.myCounterpart.getDfType(), expectedValue), false);
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
    String targetName = target.toString();
    if (rExpression != null) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(rExpression.getParent());
      if (parent instanceof PsiAssignmentExpression) {
        anchor = ((PsiAssignmentExpression)parent).getOperationSign();
        targetName = ((PsiAssignmentExpression)parent).getLExpression().getText();
      }
      else if (parent instanceof PsiVariable) {
        ASTNode node = parent.getNode();
        if (node instanceof CompositeElement) {
          anchor = ((CompositeElement)node).findChildByRoleAsPsiElement(ChildRole.INITIALIZER_EQ);
        }
        targetName = ((PsiVariable)parent).getName();
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
    return new CauseItem("'" + targetName + "' was assigned" + suffix, anchor);
  }

  private CauseItem[] findBooleanResultCauses(PsiExpression expression,
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
      if (and || tokenType.equals(JavaTokenType.OROR)) {
        PsiExpression[] operands = ((PsiPolyadicExpression)expression).getOperands();
        List<CauseItem> operandCauses = new ArrayList<>();
        for (int i = 0; i < operands.length; i++) {
          PsiExpression operand = operands[i];
          operand = PsiUtil.skipParenthesizedExprDown(operand);
          MemoryStateChange push = history.findExpressionPush(operand);
          if (push != null &&
              ((push.myInstruction instanceof ConditionalGotoInstruction &&
                ((ConditionalGotoInstruction)push.myInstruction).isTarget(value, history.myInstruction)) ||
               DfConstantType.isConst(push.myTopOfStack.getDfType(), value))) {
            CauseItem cause = new CauseItem("operand #" + (i + 1) + " of " + (and ? "&&" : "||") + "-chain is " + value, operand);
            cause.addChildren(findBooleanResultCauses(operand, push, value));
            operandCauses.add(cause);
          }
        }
        if (value != and && !operandCauses.isEmpty()) {
          return new CauseItem[]{operandCauses.get(0)};
        }
        else if (operandCauses.size() == operands.length) {
          return operandCauses.toArray(new CauseItem[0]);
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
              (leftValue instanceof DfaVariableValue || leftValue.getDfType() instanceof DfConstantType)) {
            List<CauseItem> constCauses = new ArrayList<>();
            CauseItem leftCause = constantInitializerCause(leftValue, leftChange.getExpression());
            CauseItem rightCause = constantInitializerCause(rightValue, rightChange.getExpression());
            ContainerUtil.addAllNotNull(constCauses, leftCause, rightCause);
            if (constCauses.isEmpty()) {
              return new CauseItem[]{new CauseItem("comparison arguments are the same", binOp.getOperationSign())};
            }
            return constCauses.toArray(new CauseItem[0]);
          }
          if (leftValue != rightValue && relationType.isInequality() &&
              leftValue.getDfType() instanceof DfConstantType && rightValue.getDfType() instanceof DfConstantType) {
            CauseItem causeItem = new CauseItem("comparison arguments are different constants", binOp.getOperationSign());
            causeItem.addChildren(constantInitializerCause(leftValue, leftChange.getExpression()), 
                                  constantInitializerCause(rightValue, rightChange.getExpression()));
            return new CauseItem[]{causeItem};
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
          FactDefinition<DfaNullability> nullability = operandHistory.findFact(operandValue, FactExtractor.nullability());
          if (nullability.myFact == DfaNullability.NULL) {
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

  private static CauseItem constantInitializerCause(DfaValue value, PsiExpression ref) {
    if (!(value.getDfType() instanceof DfConstantType)) return null;
    if (ref instanceof PsiReferenceExpression) {
      PsiElement target = ((PsiReferenceExpression)ref).resolve();
      if (target instanceof PsiVariable && ((PsiVariable)target).hasModifierProperty(PsiModifier.FINAL)) {
        PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(((PsiVariable)target).getInitializer());
        if (initializer != null) {
          return new CauseItem(getElementTitle(target) + " '" + ((PsiVariable)target).getName() + "' is initialized to " + 
                               initializer.getText(), initializer.getContainingFile() == ref.getContainingFile() ? initializer : ref);
        }
      }
    }
    return null;
  }

  @Nullable
  private static CauseItem findTypeCause(MemoryStateChange operandHistory, PsiType type, boolean isInstance) {
    PsiExpression operand = Objects.requireNonNull(operandHistory.getExpression());
    TypeConstraint wanted = TypeConstraints.instanceOf(type);
    PsiType operandType = operand.getType();
    if (operandType != null) {
      TypeConstraint constraint = TypeConstraints.instanceOf(operandType);
      String explanation = constraint.getAssignabilityExplanation(wanted, isInstance);
      if (explanation != null) {
        String name = "an expression";
        if (operand instanceof PsiMethodCallExpression) {
          name = "method return";
        }
        else if (operand instanceof PsiReferenceExpression) {
          name = getElementTitle(((PsiReferenceExpression)operand).resolve());
        }
        if (constraint.equals(wanted)) {
          explanation = "type is " + constraint.toShortString();
        }
        return new CauseItem(name + " " + explanation, operand);
      }
    }
    DfaValue operandValue = operandHistory.myTopOfStack;

    FactDefinition<TypeConstraint> fact = operandHistory.findFact(operandValue, FactExtractor.constraint());
    String explanation = fact.myFact.getAssignabilityExplanation(wanted, isInstance);
    while (explanation != null) {
      MemoryStateChange causeLocation = fact.myChange;
      if (causeLocation == null) break;
      MemoryStateChange prevHistory = causeLocation.getPrevious();
      if (prevHistory == null) break;
      fact = prevHistory.findFact(operandValue, FactExtractor.constraint());
      TypeConstraint prevConstraint = fact.myFact;
      String prevExplanation = prevConstraint.getAssignabilityExplanation(wanted, isInstance);
      if (prevExplanation == null) {
        if (causeLocation.myInstruction instanceof AssignInstruction && causeLocation.myTopOfStack == operandValue) {
          PsiExpression rExpression = ((AssignInstruction)causeLocation.myInstruction).getRExpression();
          if (rExpression != null) {
            MemoryStateChange rValuePush = causeLocation.findSubExpressionPush(rExpression);
            if (rValuePush != null) {
              CauseItem assignmentItem = createAssignmentCause((AssignInstruction)causeLocation.myInstruction, operandValue);
              assignmentItem.addChildren(findTypeCause(rValuePush, type, isInstance));
              return assignmentItem;
            }
          }
        }
        CauseItem causeItem = new CauseItem("an object " + explanation, operand);
        causeItem.addChildren(new CauseItem("type of '" + operand.getText() + "' is known from #ref", causeLocation));
        return causeItem;
      }
      explanation = prevExplanation;
    }
    return null;
  }

  private static String getElementTitle(PsiElement target) {
    return JavaElementKind.fromElement(target).subject();
  }

  private CauseItem @NotNull [] findRelationCause(RelationType relationType, MemoryStateChange leftChange, MemoryStateChange rightChange) {
    return findRelationCause(relationType, leftChange, leftChange.myTopOfStack, rightChange, rightChange.myTopOfStack);
  }

  private CauseItem @NotNull [] findRelationCause(RelationType relationType,
                                                  MemoryStateChange leftChange, DfaValue leftValue,
                                                  MemoryStateChange rightChange, DfaValue rightValue) {
    ProgressManager.checkCanceled();
    FactDefinition<DfaNullability> leftNullability = leftChange.findFact(leftValue, FactExtractor.nullability());
    FactDefinition<DfaNullability> rightNullability = rightChange.findFact(rightValue, FactExtractor.nullability());
    if ((leftNullability.myFact == DfaNullability.NULL && rightNullability.myFact == DfaNullability.NOT_NULL) ||
        (rightNullability.myFact == DfaNullability.NULL && leftNullability.myFact == DfaNullability.NOT_NULL)) {
      return new CauseItem[]{
        findNullabilityCause(leftChange, leftNullability.myFact),
        findNullabilityCause(rightChange, rightNullability.myFact)};
    }

    FactDefinition<LongRangeSet> leftRange = leftChange.findFact(leftValue, FactExtractor.range());
    FactDefinition<LongRangeSet> rightRange = rightChange.findFact(rightValue, FactExtractor.range());
    LongRangeSet fromRelation = rightRange.myFact.fromRelation(relationType.getNegated());
    if (fromRelation != null && !fromRelation.intersects(leftRange.myFact)) {
      return new CauseItem[]{
        findRangeCause(leftChange, leftValue, leftRange.myFact, "left operand is %s"),
        findRangeCause(rightChange, rightValue, rightRange.myFact, "right operand is %s")};
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
    if (relationType == RelationType.NE) {
      SpecialField leftField = SpecialField.fromQualifier(leftValue);
      SpecialField rightField = SpecialField.fromQualifier(rightValue);
      if (leftField != null && leftField == rightField) {
        DfaValue leftSpecial = leftField.createValue(getFactory(), leftValue);
        DfaValue rightSpecial = rightField.createValue(getFactory(), rightValue);
        CauseItem[] specialCause = findRelationCause(relationType, leftChange, leftSpecial, rightChange, rightSpecial);
        if (specialCause.length > 0) {
          CauseItem item =
            new CauseItem("values cannot be equal because " + leftValue + "." + leftField + " != " + rightValue + "." + rightField,
                          (PsiElement)null);
          item.addChildren(specialCause);
          return new CauseItem[]{item};
        }
      }
    }
    return new CauseItem[0];
  }

  private CauseItem findRelationCause(MemoryStateChange change, DfaVariableValue value,
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
      Collection<DfaRelation> relations = Collections.emptyList();
      if (expression instanceof PsiBinaryExpression) {
        DfaRelation rel = getBinaryExpressionRelation(change, (PsiBinaryExpression)expression);
        if (rel != null) {
          if (isSameRelation(rel, value, relation)) {
            return new CauseItem(new CustomDfaProblemType("condition '" + condition + "' was checked before"), expression);
          }
          relations = Collections.singleton(rel);
        }
      }
      if (expression instanceof PsiCallExpression) {
        relations = getCallRelations((PsiCallExpression)expression);
      }
      List<DfaRelation> chain = findDeductionChain(change, relations, value, relation);
      if (!chain.isEmpty()) {
        CauseItem[] result = new CauseItem[0];
        for (DfaRelation deduced : chain) {
          CauseItem[] cause =
            findRelationCause(deduced.getRelation(), change, deduced.getLeftOperand(), change, deduced.getRightOperand());
          result = ArrayUtil.mergeArrays(result, cause);
        }
        if (result.length > 1) {
          CauseItem item = new CauseItem(new CustomDfaProblemType("condition '" + condition + "' was deduced"), (PsiElement)null);
          item.addChildren(result);
          return item;
        }
      }
      return new CauseItem(new CustomDfaProblemType("it's known that '" + condition + "' from #ref"), expression);
    }
    return null;
  }

  private static List<DfaRelation> findDeductionChain(MemoryStateChange change,
                                                      Collection<DfaRelation> knownRelations,
                                                      DfaVariableValue value,
                                                      Relation relation) {
    for (DfaRelation rel : knownRelations) {
      if (isSameRelation(rel, value, relation)) {
        continue;
      }
      for (Map.Entry<DfaVariableValue, TrackingDfaMemoryState.Change> entry : change.myChanges.entrySet()) {
        DfaVariableValue actualVar = entry.getKey();
        for (Relation actualRelation : entry.getValue().myAddedRelations) {
          if (isSameRelation(rel, actualVar, actualRelation)) {
            DfaValue left;
            DfaValue right;
            RelationType type;
            if (actualRelation.myRelationType == RelationType.EQ ||
                (relation.myRelationType != RelationType.NE && relation.myRelationType == actualRelation.myRelationType)) {
              type = relation.myRelationType;
            }
            else if (relation.myRelationType == RelationType.EQ) {
              type = actualRelation.myRelationType;
            }
            else {
              continue;
            }
            if (actualVar == value) {
              left = actualRelation.myCounterpart;
              right = relation.myCounterpart;
            }
            else if (actualVar == relation.myCounterpart) {
              left = value;
              right = actualRelation.myCounterpart;
            }
            else if (actualRelation.myCounterpart == relation.myCounterpart) {
              left = value;
              right = actualVar;
            }
            else if (actualRelation.myCounterpart == value) {
              left = actualVar;
              right = relation.myCounterpart;
            }
            else {
              continue;
            }
            DfaRelation rel1 = DfaRelation.createRelation(left, type, right);
            DfaRelation rel2 = DfaRelation.createRelation(actualVar, actualRelation.myRelationType, actualRelation.myCounterpart);
            return StreamEx.of(rel1, rel2).nonNull().toImmutableList();
          }
        }
      }
    }
    return Collections.emptyList();
  }

  private static boolean isSameRelation(DfaRelation dfaRel, DfaVariableValue var, Relation relation) {
    DfaValue counterpart;
    RelationType type;
    if (dfaRel.getLeftOperand() == var) {
      type = dfaRel.getRelation();
      counterpart = dfaRel.getRightOperand();
    }
    else if (dfaRel.getRightOperand() == var) {
      type = dfaRel.getRelation().getFlipped();
      counterpart = dfaRel.getLeftOperand();
    }
    else {
      return false;
    }
    return counterpart == relation.myCounterpart && type != null;
  }

  @Nullable
  private static DfaRelation getBinaryExpressionRelation(MemoryStateChange change, PsiBinaryExpression binOp) {
    PsiExpression lOperand = binOp.getLOperand();
    PsiExpression rOperand = binOp.getROperand();
    MemoryStateChange leftPos = change.findExpressionPush(lOperand);
    MemoryStateChange rightPos = change.findExpressionPush(rOperand);
    if (leftPos != null && rightPos != null) {
      DfaValue leftValue = leftPos.myTopOfStack;
      DfaValue rightValue = rightPos.myTopOfStack;
      RelationType type = RelationType.fromElementType(binOp.getOperationTokenType());
      if (type != null) {
        return DfaRelation.createRelation(leftValue, type, rightValue);
      }
    }
    return null;
  }

  private Collection<DfaRelation> getCallRelations(PsiCallExpression callExpression) {
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(callExpression);
    Set<DfaRelation> results = new LinkedHashSet<>();
    for (MethodContract contract : contracts) {
      for (ContractValue condition : contract.getConditions()) {
        DfaCondition rel = condition.fromCall(getFactory(), callExpression);
        ContainerUtil.addIfNotNull(results, ObjectUtils.tryCast(rel, DfaRelation.class));
      }
    }
    return results;
  }

  private CauseItem findNullabilityCause(MemoryStateChange factUse, DfaNullability nullability) {
    PsiExpression expression = factUse.getExpression();
    if (expression instanceof PsiTypeCastExpression) {
      MemoryStateChange operandPush = factUse.findSubExpressionPush(((PsiTypeCastExpression)expression).getOperand());
      if (operandPush != null) {
        return findNullabilityCause(operandPush, nullability);
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      PsiMethod method = call.resolveMethod();
      CauseItem causeItem = fromMemberNullability(nullability, method, JavaElementKind.METHOD, 
                                                  call.getMethodExpression().getReferenceNameElement());
      if (causeItem == null) {
        switch (nullability) {
          case NULL:
          case NULLABLE:
            causeItem = fromCallContract(factUse, call, ContractReturnValue.returnNull());
            break;
          case NOT_NULL:
            causeItem = fromCallContract(factUse, call, ContractReturnValue.returnNotNull());
            break;
          default:
        }
      }
      if (causeItem != null) {
        return causeItem;
      }
    }
    if (expression instanceof PsiReferenceExpression) {
      PsiVariable variable = ObjectUtils.tryCast(((PsiReferenceExpression)expression).resolve(), PsiVariable.class);
      if (variable != null) {
        CauseItem causeItem = fromMemberNullability(nullability, variable, JavaElementKind.fromElement(variable), expression);
        if (causeItem != null) {
          return causeItem;
        }
      }
    }
    FactDefinition<DfaNullability> info = factUse.findFact(factUse.myTopOfStack, FactExtractor.nullability());
    MemoryStateChange factDef = info.myFact == nullability ? info.myChange : null;
    if (nullability == DfaNullability.NOT_NULL) {
      String explanation = getObviouslyNonNullExplanation(expression);
      if (explanation != null) {
        return new CauseItem("expression cannot be null as it's " + explanation, expression);
      }
      if (factDef != null) {
        if (factDef.myInstruction instanceof CheckNotNullInstruction) {
          NullabilityProblemKind.NullabilityProblem<?> problem = ((CheckNotNullInstruction)factDef.myInstruction).getProblem();
          PsiExpression dereferenced = problem.getDereferencedExpression();
          String text = dereferenced == null ? factUse.myTopOfStack.toString() : dereferenced.getText();
          if (dereferenced != null && problem.getKind() == NullabilityProblemKind.passingToNotNullParameter) {
            PsiExpression arg = dereferenced;
            while (arg.getParent() instanceof PsiParenthesizedExpression) {
              arg = (PsiExpression)arg.getParent();
            }
            PsiParameter parameter = MethodCallUtils.getParameterForArgument(dereferenced);
            if (parameter != null) {
              CauseItem item =
                new CauseItem("'" + text + "' was passed as an argument to a method accepting non-null parameter", dereferenced);
              item.addChildren(fromMemberNullability(DfaNullability.NOT_NULL, parameter, JavaElementKind.PARAMETER, dereferenced));
              return item;
            }
          }
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
            assignmentItem.addChildren(findNullabilityCause(rValuePush, nullability));
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

  private CauseItem fromMemberNullability(DfaNullability nullability, PsiModifierListOwner owner,
                                          JavaElementKind memberKind, PsiElement anchor) {
    if (owner != null) {
      NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(owner.getProject()).findEffectiveNullabilityInfo(owner);
      String name = ((PsiNamedElement)owner).getName();
      if (info != null && DfaNullability.fromNullability(info.getNullability()) == nullability) {
        String message;
        if (info.isInferred()) {
          if (owner instanceof PsiParameter &&
              anchor instanceof PsiReferenceExpression &&
              ((PsiReferenceExpression)anchor).isReferenceTo(owner)) {
            // Do not use inference inside method itself
            return null;
          }
          message = memberKind.subject() + " '" + name + "' was inferred to be '" + nullability.getPresentationName() + "'";
        }
        else if (info.isExternal()) {
          message = memberKind.subject() + " '" + name + "' is externally annotated as '" + nullability.getPresentationName() + "'";
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
            memberKind.subject() + " '" + name + "' inherits " + details + ", thus '" + nullability.getPresentationName() + "'";
        }
        else {
          message = memberKind.subject() + " '" + name + "' is annotated as '" + nullability.getPresentationName() + "'";
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
      if (owner instanceof PsiField && getFactory().canTrustFieldInitializer((PsiField)owner)) {
        Pair<PsiExpression, Nullability> fieldNullability =
          NullabilityUtil.getNullabilityFromFieldInitializers((PsiField)owner, Nullability.UNKNOWN);
        if (fieldNullability.second == DfaNullability.toNullability(nullability)) {
          PsiExpression initializer = fieldNullability.first; 
          if (initializer != null) {
            if (initializer.getContainingFile() == anchor.getContainingFile()) {
              anchor = initializer;
            }
            return new CauseItem("field '" + name + "' is initialized to '" +
                                 DfaNullability.fromNullability(fieldNullability.second).getPresentationName() + "' value", anchor);
          }
          if (owner.getContainingFile() == anchor.getContainingFile()) {
            anchor = owner;
          }
          return new CauseItem("field '" + name + "' is known to be always initialized to '" +
                               DfaNullability.fromNullability(fieldNullability.second).getPresentationName() + "' value", anchor);
        }
      }
    }
    return null;
  }

  private CauseItem fromCallContract(MemoryStateChange history, PsiCallExpression call, ContractReturnValue contractReturnValue) {
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, call);
    if (contracts.isEmpty()) return null;
    MethodContract contract = contracts.get(0);
    String contractType = JavaMethodContractUtil.hasExplicitContractAnnotation(method) ? "" :
                          contracts.stream().allMatch(c -> c instanceof StandardMethodContract) ? "inferred " :
                          "hard-coded ";
    if (call instanceof PsiMethodCallExpression) {
      PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)call).getMethodExpression();
      String name = methodExpression.getReferenceName();
      String prefix = "according to " + contractType + "contract, method '" + name + "'";
      if (contracts.size() == 1 && contract.isTrivial() && contractReturnValue.isSuperValueOf(contract.getReturnValue())) {
        return new CauseItem(prefix +
                             " always returns '" + contract.getReturnValue() + "' value",
                             methodExpression.getReferenceNameElement());
      }
      List<? extends MethodContract> nonIntersecting = MethodContract.toNonIntersectingContracts(contracts);
      if (nonIntersecting != null) {
        MethodContract onlyContract = ContainerUtil
          .getOnlyItem(ContainerUtil.filter(nonIntersecting, mc -> contractReturnValue.isSuperValueOf(mc.getReturnValue())));
        if (onlyContract != null) {
          return fromSingleContract(history, (PsiMethodCallExpression)call, method, prefix, onlyContract);
        }
      }
      for (MethodContract c : contracts) {
        ThreeState applies = contractApplies((PsiMethodCallExpression)call, c);
        if (applies == ThreeState.NO) {
          continue;
        }
        if (applies == ThreeState.UNSURE) {
          break;
        }
        if (applies == ThreeState.YES) {
          if (contractReturnValue.isSuperValueOf(c.getReturnValue())) {
            return fromSingleContract(history, (PsiMethodCallExpression)call, method, prefix, c);
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private ThreeState contractApplies(@NotNull PsiMethodCallExpression call, @NotNull MethodContract contract) {
    List<ContractValue> conditions = contract.getConditions();
    for (ContractValue condition : conditions) {
      DfaCondition cond = condition.fromCall(getFactory(), call);
      if (cond == DfaCondition.getTrue()) return ThreeState.YES;
      if (cond == DfaCondition.getFalse()) return ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  @NotNull
  private CauseItem fromSingleContract(@NotNull MemoryStateChange history, @NotNull PsiMethodCallExpression call,
                                       @NotNull PsiMethod method, @NotNull String prefix, @NotNull MethodContract contract) {
    List<ContractValue> conditions = contract.getConditions();
    String conditionsText = StringUtil.join(conditions, c -> c.getPresentationText(method), " and ");
    String returnValueText = contract.getReturnValue().isFail() ? "throws exception" : "returns '" + contract.getReturnValue() + "' value";
    CauseItem causeItem = new CauseItem(prefix + " " + returnValueText + " when " + conditionsText,
                                        call.getMethodExpression().getReferenceNameElement());
    for (ContractValue contractValue : conditions) {
      if (!(contractValue instanceof ContractValue.Condition)) {
        continue;
      }
      ContractValue.Condition condition = (ContractValue.Condition)contractValue;
      ContractValue leftVal = condition.getLeft();
      ContractValue rightVal = condition.getRight();
      RelationType type = condition.getRelationType();
      DfaCallArguments arguments = DfaCallArguments.fromCall(getFactory(), call);
      PsiExpression leftPlace = leftVal.findPlace(call);
      MemoryStateChange leftPush = history.findSubExpressionPush(leftPlace);
      if (leftPush == null && arguments != null) {
        DfaValue left = leftVal.makeDfaValue(getFactory(), arguments);
        leftPush = MemoryStateChange.create(history.getPrevious(), new PushInstruction(left, null), Collections.emptyMap(), left);
      }
      PsiExpression rightPlace = rightVal.findPlace(call);
      MemoryStateChange rightPush = history.findSubExpressionPush(rightPlace);
      if (rightPush == null && arguments != null) {
        DfaValue right = rightVal.makeDfaValue(getFactory(), arguments);
        rightPush = MemoryStateChange.create(history.getPrevious(), new PushInstruction(right, null), Collections.emptyMap(), right);
      }
      if (leftPush != null && rightPush != null) {
        causeItem.addChildren(findRelationCause(type, leftPush, rightPush));
      }
    }
    return causeItem;
  }

  private static CauseItem findRangeCause(MemoryStateChange factUse, DfaValue value, LongRangeSet range, String template) {
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
    PsiExpression expression = factUse.myTopOfStack == value ? factUse.getExpression() : null;
    if (expression != null) {
      PsiType type = expression.getType();
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
      if (expression instanceof PsiTypeCastExpression && type instanceof PsiPrimitiveType && TypeConversionUtil.isNumericType(type)) {
        PsiExpression operand = ((PsiTypeCastExpression)expression).getOperand();
        MemoryStateChange operandPush = factUse.findExpressionPush(operand);
        if (operandPush != null) {
          DfaValue castedValue = operandPush.myTopOfStack;
          FactDefinition<LongRangeSet> operandInfo = operandPush.findFact(castedValue, FactExtractor.range());
          LongRangeSet operandRange = operandInfo.myFact;
          LongRangeSet result = operandRange.castTo((PsiPrimitiveType)type);
          if (range.equals(result)) {
            CauseItem cause =
              new CauseItem(new RangeDfaProblemType("result of '(" + type.getCanonicalText() + ")' cast is %s", range, null), expression);
            if (!operandRange.equals(LongRangeSet.fromType(operand.getType()))) {
              cause.addChildren(findRangeCause(operandPush, castedValue, operandRange, "cast operand is %s"));
            }
            return cause;
          }
        }
      }
      if (range.equals(LongRangeSet.fromType(type))) {
        return null; // Range is any value of given type: no need to explain (except narrowing cast)
      }
      if (PsiType.LONG.equals(type) || PsiType.INT.equals(type)) {
        if (expression instanceof PsiBinaryExpression) {
          boolean isLong = PsiType.LONG.equals(type);
          PsiBinaryExpression binOp = (PsiBinaryExpression)expression;
          PsiExpression left = PsiUtil.skipParenthesizedExprDown(binOp.getLOperand());
          PsiExpression right = PsiUtil.skipParenthesizedExprDown(binOp.getROperand());
          MemoryStateChange leftPush = factUse.findExpressionPush(left);
          MemoryStateChange rightPush = factUse.findExpressionPush(right);
          if (leftPush != null && rightPush != null) {
            DfaValue leftVal = leftPush.myTopOfStack;
            FactDefinition<LongRangeSet> leftSet = leftPush.findFact(leftVal, FactExtractor.range());
            DfaValue rightVal = rightPush.myTopOfStack;
            FactDefinition<LongRangeSet> rightSet = rightPush.findFact(rightVal, FactExtractor.range());
            LongRangeSet fromType = Objects.requireNonNull(LongRangeSet.fromType(type));
            LongRangeSet leftRange = leftSet.myFact.intersect(fromType);
            LongRangeSet rightRange = rightSet.myFact.intersect(fromType);
            LongRangeSet result = leftRange.binOpFromToken(binOp.getOperationTokenType(), rightRange, isLong);
            if (range.equals(result)) {
              String sign = binOp.getOperationSign().getText();
              CauseItem cause = new CauseItem(new RangeDfaProblemType("result of '" + (sign.equals("%")?"%%":sign) + "' is %s", 
                                                                      range, ObjectUtils.tryCast(type, PsiPrimitiveType.class)), factUse);
              CauseItem leftCause = null, rightCause = null;
              if (!leftRange.equals(fromType)) {
                leftCause = findRangeCause(leftPush, leftVal, leftRange, "left operand is %s");
              }
              if (!rightRange.equals(fromType)) {
                rightCause = findRangeCause(rightPush, rightVal, rightRange, "right operand is %s");
              }
              cause.addChildren(leftCause, rightCause);
              return cause;
            }
          }
        }
      }
    }
    PsiPrimitiveType type = expression != null ? ObjectUtils.tryCast(expression.getType(), PsiPrimitiveType.class) : null;
    CauseItem item = new CauseItem(new RangeDfaProblemType(template, range, type), factUse);
    FactDefinition<LongRangeSet> info = factUse.findFact(value, FactExtractor.range());
    MemoryStateChange factDef = range.equals(info.myFact) ? info.myChange : null;
    if (factDef != null) {
      if (factDef.myInstruction instanceof AssignInstruction && factDef.myTopOfStack == value) {
        PsiExpression rExpression = ((AssignInstruction)factDef.myInstruction).getRExpression();
        if (rExpression != null) {
          MemoryStateChange rValuePush = factDef.findSubExpressionPush(rExpression);
          if (rValuePush != null) {
            CauseItem assignmentItem = createAssignmentCause((AssignInstruction)factDef.myInstruction, value);
            assignmentItem.addChildren(findRangeCause(rValuePush, rValuePush.myTopOfStack, range, "Value is %s"));
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
    if (relation.myRelationType == RelationType.NE && relation.myCounterpart.getDfType() instanceof DfConstantType) {
      return history.findRelation(var, rel -> rel.equals(relation) ||
                                              rel.myRelationType == RelationType.EQ &&
                                              rel.myCounterpart.getDfType() instanceof DfConstantType,
                                  true);
    }
    MemoryStateChange exact = history.findRelation(var, rel -> rel.myCounterpart == relation.myCounterpart &&
                                                                   relation.myRelationType.equals(rel.myRelationType), true);
    if (exact != null) {
      return exact;
    }
    return history.findRelation(var, rel -> rel.myCounterpart == relation.myCounterpart &&
                                            relation.myRelationType.isSubRelation(rel.myRelationType), true);
  }
}
