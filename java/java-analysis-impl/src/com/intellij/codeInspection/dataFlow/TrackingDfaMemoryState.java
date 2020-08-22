// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.AssignInstruction;
import com.intellij.codeInspection.dataFlow.instructions.ConditionalGotoInstruction;
import com.intellij.codeInspection.dataFlow.instructions.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class TrackingDfaMemoryState extends DfaMemoryStateImpl {
  private MemoryStateChange myHistory;

  protected TrackingDfaMemoryState(DfaValueFactory factory) {
    super(factory);
    myHistory = null;
  }

  protected TrackingDfaMemoryState(TrackingDfaMemoryState toCopy) {
    super(toCopy);
    myHistory = toCopy.myHistory;
  }

  @NotNull
  @Override
  public TrackingDfaMemoryState createCopy() {
    return new TrackingDfaMemoryState(this);
  }

  @Override
  protected void afterMerge(DfaMemoryStateImpl other) {
    super.afterMerge(other);
    assert other instanceof TrackingDfaMemoryState;
    myHistory = myHistory.merge(((TrackingDfaMemoryState)other).myHistory);
  }

  private Map<DfaVariableValue, Set<Relation>> getRelations() {
    Map<DfaVariableValue, Set<Relation>> result = new HashMap<>();
    forRecordedVariableTypes((var, type) -> {
      if (type instanceof DfConstantType) {
        result.computeIfAbsent(var, k -> new HashSet<>()).add(new Relation(RelationType.EQ, getFactory().fromDfType(type)));
      }
      if (type instanceof DfAntiConstantType) {
        Set<?> notValues = ((DfAntiConstantType<?>)type).getNotValues();
        PsiType varType = var.getType();
        if (!notValues.isEmpty() && varType != null) {
          for (Object notValue : notValues) {
            result.computeIfAbsent(var, k -> new HashSet<>()).add(
              new Relation(RelationType.NE, getFactory().fromDfType(DfTypes.constant(notValue, varType))));
          }
        }
      }
    });

    for (EqClass eqClass : getNonTrivialEqClasses()) {
      for (DfaVariableValue var : eqClass) {
        Set<Relation> set = result.computeIfAbsent(var, k -> new HashSet<>());
        for (DfaVariableValue eqVar : eqClass) {
          if (eqVar != var) {
            set.add(new Relation(RelationType.EQ, eqVar));
          }
        }
      }
    }

    for (DistinctPairSet.DistinctPair classPair : getDistinctClassPairs()) {
      EqClass first = classPair.getFirst();
      EqClass second = classPair.getSecond();
      RelationType plain = classPair.isOrdered() ? RelationType.LT : RelationType.NE;
      RelationType flipped = Objects.requireNonNull(plain.getFlipped());

      for (DfaVariableValue var1 : first) {
        for (DfaVariableValue var2 : second) {
          result.computeIfAbsent(var1, k -> new HashSet<>()).add(new Relation(plain, var2));
          result.computeIfAbsent(var2, k -> new HashSet<>()).add(new Relation(flipped, var1));
        }
      }
    }

    return result;
  }


  void recordChange(Instruction instruction, TrackingDfaMemoryState previous) {
    Map<DfaVariableValue, Change> result = getChangeMap(previous);
    DfaValue value = isEmptyStack() ? getFactory().getUnknown() : peek();
    myHistory = MemoryStateChange.create(myHistory, instruction, result, value);
  }

  @NotNull
  private Map<DfaVariableValue, Change> getChangeMap(TrackingDfaMemoryState previous) {
    Map<DfaVariableValue, Change> changeMap = new HashMap<>();
    Set<DfaVariableValue> varsToCheck = new HashSet<>();
    previous.forRecordedVariableTypes((value, state) -> varsToCheck.add(value));
    forRecordedVariableTypes((value, state) -> varsToCheck.add(value));
    for (DfaVariableValue value : varsToCheck) {
      DfType newType = getDfType(value);
      DfType oldType = previous.getDfType(value);
      if (!newType.equals(oldType)) {
        changeMap.put(value, new Change(Collections.emptySet(), Collections.emptySet(), oldType, newType));
      }
    }
    Map<DfaVariableValue, Set<Relation>> oldRelations = previous.getRelations();
    Map<DfaVariableValue, Set<Relation>> newRelations = getRelations();
    varsToCheck.clear();
    varsToCheck.addAll(oldRelations.keySet());
    varsToCheck.addAll(newRelations.keySet());
    for (DfaVariableValue value : varsToCheck) {
      Set<Relation> oldValueRelations = oldRelations.getOrDefault(value, Collections.emptySet());
      Set<Relation> newValueRelations = newRelations.getOrDefault(value, Collections.emptySet());
      if (!oldValueRelations.equals(newValueRelations)) {
        Set<Relation> added = new HashSet<>(newValueRelations);
        added.removeAll(oldValueRelations);
        Set<Relation> removed = new HashSet<>(oldValueRelations);
        removed.removeAll(newValueRelations);
        changeMap.compute(
          value, (v, change) -> change == null
                                ? Change.create(removed, added, DfTypes.BOTTOM, DfTypes.BOTTOM)
                                : Change.create(removed, added, change.myOldType, change.myNewType));
      }
    }
    return changeMap;
  }

  MemoryStateChange getHistory() {
    return myHistory;
  }

  /**
   * Records a bridge changes. A bridge states are states which process the same input instruction,
   * but in result jump to another place in the program (other than this state target).
   * A bridge change is the difference between this state and all states which have different
   * target instruction. Bridges allow to track what else is processed in parallel with current state,
   * including states which may not arrive into target place. E.g. consider two states like this:
   *
   * <pre>
   *   this_state  other_state
   *       |            |
   *       some_condition    <-- bridge is recorded here
   *       |(true)      |(false)
   *       |         return
   *       |
   *   always_true_condition <-- explanation is requested here
   * </pre>
   *
   * Thanks to the bridge we know that {@code some_condition} could be important for
   * {@code always_true_condition} explanation.
   *
   * @param instruction instruction which
   * @param bridgeStates
   */
  void addBridge(Instruction instruction, List<TrackingDfaMemoryState> bridgeStates) {
    Map<DfaVariableValue, Change> changeMap = null;
    for (TrackingDfaMemoryState bridge : bridgeStates) {
      Map<DfaVariableValue, Change> newChangeMap = getChangeMap(bridge);
      if (changeMap == null) {
        changeMap = newChangeMap;
      } else {
        changeMap.keySet().retainAll(newChangeMap.keySet());
        changeMap.replaceAll((var, old) -> old.unite(newChangeMap.get(var)));
        changeMap.values().removeIf(Objects::isNull);
      }
      if (changeMap.isEmpty()) {
        break;
      }
    }
    if (changeMap != null && !changeMap.isEmpty()) {
      myHistory = myHistory.withBridge(instruction, changeMap);
    }
  }

  static class Relation {
    final @NotNull RelationType myRelationType;
    final @NotNull DfaValue myCounterpart;

    Relation(@NotNull RelationType type, @NotNull DfaValue counterpart) {
      myRelationType = type;
      myCounterpart = counterpart;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Relation relation = (Relation)o;
      return myRelationType == relation.myRelationType &&
             myCounterpart.equals(relation.myCounterpart);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myRelationType, myCounterpart);
    }

    @Override
    public String toString() {
      return myRelationType + " " + myCounterpart;
    }
  }

  static final class Change {
    final @NotNull Set<Relation> myRemovedRelations;
    final @NotNull Set<Relation> myAddedRelations;
    final @NotNull DfType myOldType;
    final @NotNull DfType myNewType;

    private Change(@NotNull Set<Relation> removedRelations, @NotNull Set<Relation> addedRelations, @NotNull DfType oldType, @NotNull DfType newType) {
      myRemovedRelations = removedRelations.isEmpty() ? Collections.emptySet() : removedRelations;
      myAddedRelations = addedRelations.isEmpty() ? Collections.emptySet() : addedRelations;
      myOldType = oldType;
      myNewType = newType;
    }

    @Nullable
    static Change create(Set<Relation> removedRelations, Set<Relation> addedRelations, DfType oldType, DfType newType) {
      if (removedRelations.isEmpty() && addedRelations.isEmpty() && oldType == DfTypes.BOTTOM && newType == DfTypes.BOTTOM) {
        return null;
      }
      return new Change(removedRelations, addedRelations, oldType, newType);
    }

    /**
     * Creates a Change which reflects changes actual for both this and other change
     * @param other other change to unite with
     * @return new change or null if this and other change has nothing in common
     */
    @Nullable
    Change unite(Change other) {
      Set<Relation> added = new HashSet<>(ContainerUtil.intersection(myAddedRelations, other.myAddedRelations));
      Set<Relation> removed = new HashSet<>(ContainerUtil.intersection(myRemovedRelations, other.myRemovedRelations));
      DfType oldType = myOldType.join(other.myOldType);
      DfType newType = myNewType.join(other.myNewType);
      if (oldType.equals(newType)) {
        oldType = newType = DfTypes.BOTTOM;
      }
      return create(removed, added, oldType, newType);
    }

    @Override
    public String toString() {
      String removed = StreamEx.of(myRemovedRelations).map(Object::toString).append(myOldType.toString())
        .without("").joining(", ");
      String added = StreamEx.of(myAddedRelations).map(Object::toString).append(myNewType.toString())
        .without("").joining(", ");
      return (removed.isEmpty() ? "" : "-{" + removed + "} ") + (added.isEmpty() ? "" : "+{" + added + "}");
    }
  }

  static final class MemoryStateChange {
    private final @NotNull List<MemoryStateChange> myPrevious;
    final @NotNull Instruction myInstruction;
    final @NotNull Map<DfaVariableValue, Change> myChanges;
    final @NotNull DfaValue myTopOfStack;
    final @NotNull Map<DfaVariableValue, Change> myBridgeChanges;
    int myCursor = 0;

    private MemoryStateChange(@NotNull List<MemoryStateChange> previous,
                              @NotNull Instruction instruction,
                              @NotNull Map<DfaVariableValue, Change> changes,
                              @NotNull DfaValue topOfStack,
                              @NotNull Map<DfaVariableValue, Change> bridgeChanges) {
      myPrevious = previous;
      myInstruction = instruction;
      myChanges = changes;
      myTopOfStack = topOfStack;
      myBridgeChanges = bridgeChanges;
    }

    void reset() {
      for (MemoryStateChange change = this; change != null; change = change.getPrevious()) {
        change.myCursor = 0;
      }
    }

    boolean advance() {
      if (myCursor < myPrevious.size() && !myPrevious.get(myCursor).advance()) {
        myCursor++;
        MemoryStateChange previous = getPrevious();
        if (previous != null) {
          previous.reset();
        }
      }
      return myCursor < myPrevious.size();
    }

    @Contract("null -> null")
    @Nullable
    MemoryStateChange findExpressionPush(@Nullable PsiExpression expression) {
      if (expression == null) return null;
      return findChange(change -> change.getExpression() == expression, false);
    }

    @Contract("null -> null")
    @Nullable
    MemoryStateChange findSubExpressionPush(@Nullable PsiExpression expression) {
      if (expression == null) return null;
      PsiElement topElement = ExpressionUtils.getPassThroughParent(expression);
      return findChange(change -> {
        PsiExpression changeExpression = change.getExpression();
        if (changeExpression == null) return false;
        return changeExpression == expression ||
               (PsiTreeUtil.isAncestor(expression, changeExpression, true) &&
                ExpressionUtils.getPassThroughParent(changeExpression) == topElement);
      }, false);
    }

    MemoryStateChange findRelation(DfaVariableValue value, @NotNull Predicate<Relation> relationPredicate, boolean startFromSelf) {
      return findChange(change -> {
        if (change.myInstruction instanceof AssignInstruction && change.myTopOfStack == value) return true;
        Change varChange = change.myChanges.get(value);
        if (varChange != null && varChange.myAddedRelations.stream().anyMatch(relationPredicate)) return true;
        Change bridgeVarChange = change.myBridgeChanges.get(value);
        return bridgeVarChange != null && bridgeVarChange.myAddedRelations.stream().anyMatch(relationPredicate);
      }, startFromSelf);
    }

    @NotNull
    <T> FactDefinition<T> findFact(DfaValue value, FactExtractor<T> extractor) {
      if (value instanceof DfaVariableValue) {
        for (MemoryStateChange change = this; change != null; change = change.getPrevious()) {
          FactDefinition<T> factPair = factFromChange(extractor, change, change.myChanges.get(value));
          if (factPair != null) return factPair;
          if (!(change.myInstruction instanceof ConditionalGotoInstruction)) {
            factPair = factFromChange(extractor, change, change.myBridgeChanges.get(value));
            if (factPair != null) return factPair;
          }
          if (change.myInstruction instanceof AssignInstruction && change.myTopOfStack == value && change.getPrevious() != null) {
            FactDefinition<T> fact = change.getPrevious().findFact(value, extractor);
            return new FactDefinition<>(change, fact.myFact);
          }
        }
        return new FactDefinition<>(null, extractor.extract(((DfaVariableValue)value).getInherentType()));
      }
      if (value instanceof DfaBinOpValue) {
        FactDefinition<T> left = findFact(((DfaBinOpValue)value).getLeft(), extractor);
        FactDefinition<T> right = findFact(((DfaBinOpValue)value).getRight(), extractor);
        if (left.myFact instanceof LongRangeSet && right.myFact instanceof LongRangeSet) {
          @SuppressWarnings("unchecked") T result = (T)((LongRangeSet)left.myFact).binOpFromToken(
            ((DfaBinOpValue)value).getTokenType(), ((LongRangeSet)right.myFact), PsiType.LONG.equals(value.getType()));
          return new FactDefinition<>(null, Objects.requireNonNull(result));
        }
      }
      return new FactDefinition<>(null, extractor.extract(value.getDfType()));
    }

    @Nullable
    MemoryStateChange getPrevious() {
      return myCursor == myPrevious.size() ? null : myPrevious.get(myCursor);
    }

    public MemoryStateChange getNonMerge() {
      MemoryStateChange change = myInstruction instanceof MergeInstruction ? getPrevious() : this;
      assert change == null || !(change.myInstruction instanceof MergeInstruction);
      return change;
    }

    @Nullable
    private static <T> FactDefinition<T> factFromChange(FactExtractor<T> extractor, MemoryStateChange change, Change varChange) {
      if (varChange != null) {
        T newFact = extractor.extract(varChange.myNewType);
        T oldFact = extractor.extract(varChange.myOldType);
        if (!newFact.equals(oldFact)) {
          return new FactDefinition<>(change, newFact);
        }
      }
      return null;
    }

    @Nullable
    private MemoryStateChange findChange(@NotNull Predicate<MemoryStateChange> predicate, boolean startFromSelf) {
      for (MemoryStateChange change = startFromSelf ? this : getPrevious(); change != null; change = change.getPrevious()) {
        if (predicate.test(change)) {
          return change;
        }
      }
      return null;
    }

    @Nullable
    PsiExpression getExpression() {
      if (myInstruction instanceof ExpressionPushingInstruction &&
          ((ExpressionPushingInstruction<?>)myInstruction).getExpressionRange() == null) {
        return ((ExpressionPushingInstruction<?>)myInstruction).getExpression();
      }
      if (myInstruction instanceof ConditionalGotoInstruction) {
        return ObjectUtils.tryCast(((ConditionalGotoInstruction)myInstruction).getPsiAnchor(), PsiExpression.class);
      }
      return null;
    }

    @NotNull
    public MemoryStateChange merge(MemoryStateChange change) {
      if (change == this) return this;
      Set<MemoryStateChange> previous = new LinkedHashSet<>();
      if (myInstruction instanceof MergeInstruction) {
        previous.addAll(myPrevious);
      } else {
        previous.add(this);
      }
      if (change.myInstruction instanceof MergeInstruction) {
        previous.addAll(change.myPrevious);
      } else {
        previous.add(change);
      }
      if (previous.size() == 1) {
        return previous.iterator().next();
      }
      return new MemoryStateChange(new ArrayList<>(previous), new MergeInstruction(), Collections.emptyMap(), myTopOfStack.getFactory().getUnknown(),
                                   Collections.emptyMap());
    }

    MemoryStateChange withBridge(@NotNull Instruction instruction, @NotNull Map<DfaVariableValue, Change> bridge) {
      if (myInstruction != instruction) {
        if (instruction instanceof ConditionalGotoInstruction &&
            getExpression() == ((ConditionalGotoInstruction)instruction).getPsiAnchor()) {
          instruction = myInstruction;
        } else {
          return new MemoryStateChange(
            Collections.singletonList(this), instruction, Collections.emptyMap(), myTopOfStack.getFactory().getUnknown(), bridge);
        }
      }
      assert myBridgeChanges.isEmpty();
      return new MemoryStateChange(myPrevious, instruction, myChanges, myTopOfStack, bridge);
    }

    @Nullable
    static MemoryStateChange create(@Nullable MemoryStateChange previous,
                                    @NotNull Instruction instruction,
                                    @NotNull Map<DfaVariableValue, Change> result,
                                    @NotNull DfaValue value) {
      if (result.isEmpty() && DfaTypeValue.isUnknown(value)) {
        return previous;
      }
      return new MemoryStateChange(ContainerUtil.createMaybeSingletonList(previous), instruction, result, value, Collections.emptyMap());
    }

    MemoryStateChange[] flatten() {
      List<MemoryStateChange> changes = StreamEx.iterate(this, Objects::nonNull, change -> change.getPrevious()).toList();
      Collections.reverse(changes);
      return changes.toArray(new MemoryStateChange[0]);
    }

    String dump() {
      return StreamEx.of(flatten()).joining("\n");
    }

    @Override
    public String toString() {
      return myInstruction.getIndex() + " " + myInstruction + ": " + myTopOfStack +
             (myChanges.isEmpty() ? "" :
              "; Changes: " + EntryStream.of(myChanges).join(": ", "\n\t", "").joining()) +
             (myBridgeChanges.isEmpty() ? "" :
              "; Bridge changes: " + EntryStream.of(myBridgeChanges).join(": ", "\n\t", "").joining());
    }
  }

  private static class MergeInstruction extends Instruction {
    @Override
    public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
      return DfaInstructionState.EMPTY_ARRAY;
    }

    @Override
    public String toString() {
      return "STATE_MERGE";
    }
  }

  static class FactDefinition<T> {
    final @Nullable MemoryStateChange myChange;
    final @NotNull T myFact;

    FactDefinition(@Nullable MemoryStateChange change, @NotNull T fact) {
      myChange = change;
      myFact = fact;
    }

    @Override
    public String toString() {
      return myFact + " @ " + myChange;
    }
  }

  interface FactExtractor<T> {
    @NotNull T extract(DfType type);

    static FactExtractor<DfaNullability> nullability() {
      return DfaNullability::fromDfType;
    }

    static FactExtractor<TypeConstraint> constraint() {
      return TypeConstraint::fromDfType;
    }

    static FactExtractor<LongRangeSet> range() {
      return DfLongType::extractRange;
    }
  }
}
