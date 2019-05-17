// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.AssignInstruction;
import com.intellij.codeInspection.dataFlow.instructions.ConditionalGotoInstruction;
import com.intellij.codeInspection.dataFlow.instructions.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
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
    for (EqClass eqClass : getNonTrivialEqClasses()) {
      DfaConstValue constant = eqClass.findConstant();
      List<DfaVariableValue> vars = eqClass.getVariables(false);
      for (DfaVariableValue var : vars) {
        Set<Relation> set = result.computeIfAbsent(var, k -> new HashSet<>());
        if (constant != null) {
          set.add(new Relation(RelationType.EQ, constant));
        }
        for (DfaVariableValue eqVar : vars) {
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

      List<DfaVariableValue> firstVars = first.getVariables(false);
      List<DfaVariableValue> secondVars = second.getVariables(false);
      for (DfaVariableValue var1 : firstVars) {
        for (DfaVariableValue var2 : secondVars) {
          result.computeIfAbsent(var1, k -> new HashSet<>()).add(new Relation(plain, var2));
          result.computeIfAbsent(var2, k -> new HashSet<>()).add(new Relation(flipped, var1));
        }
      }
      DfaConstValue firstConst = first.findConstant();
      if (firstConst != null) {
        for (DfaVariableValue var2 : secondVars) {
          result.computeIfAbsent(var2, k -> new HashSet<>()).add(new Relation(flipped, firstConst));
        }
      }
      DfaConstValue secondConst = second.findConstant();
      if (secondConst != null) {
        for (DfaVariableValue var1 : firstVars) {
          result.computeIfAbsent(var1, k -> new HashSet<>()).add(new Relation(plain, secondConst));
        }
      }
    }

    return result;
  }


  void recordChange(Instruction instruction, TrackingDfaMemoryState previous) {
    Map<DfaVariableValue, Change> result = getChangeMap(previous);
    DfaValue value = isEmptyStack() ? DfaUnknownValue.getInstance() : peek();
    myHistory = MemoryStateChange.create(myHistory, instruction, result, value);
  }

  @NotNull
  private Map<DfaVariableValue, Change> getChangeMap(TrackingDfaMemoryState previous) {
    Map<DfaVariableValue, Change> changeMap = new HashMap<>();
    Set<DfaVariableValue> varsToCheck = new HashSet<>();
    previous.forVariableStates((value, state) -> varsToCheck.add(value));
    forVariableStates((value, state) -> varsToCheck.add(value));
    for (DfaVariableValue value : varsToCheck) {
      DfaFactMap newMap = getVariableState(value).myFactMap;
      DfaFactMap oldMap = previous.getVariableState(value).myFactMap;
      if (!newMap.equals(oldMap)) {
        DfaFactMap added = DfaFactMap.EMPTY;
        DfaFactMap removed = DfaFactMap.EMPTY;
        for (DfaFactType<?> type : DfaFactType.getTypes()) {
          Object oldVal = oldMap.get(type);
          Object newVal = newMap.get(type);
          if (!Objects.equals(oldVal, newVal)) {
            //noinspection unchecked
            added = added.with((DfaFactType<Object>)type, newVal);
            //noinspection unchecked
            removed = removed.with((DfaFactType<Object>)type, oldVal);
          }
        }
        changeMap.put(value, new Change(Collections.emptySet(), Collections.emptySet(), removed, added));
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
                                ? Change.create(removed, added, DfaFactMap.EMPTY, DfaFactMap.EMPTY)
                                : Change.create(removed, added, change.myRemovedFacts, change.myAddedFacts));
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

  static class Change {
    final Set<Relation> myRemovedRelations;
    final Set<Relation> myAddedRelations;
    final DfaFactMap myRemovedFacts;
    final DfaFactMap myAddedFacts;

    private Change(Set<Relation> removedRelations, Set<Relation> addedRelations, DfaFactMap removedFacts, DfaFactMap addedFacts) {
      myRemovedRelations = removedRelations.isEmpty() ? Collections.emptySet() : removedRelations;
      myAddedRelations = addedRelations.isEmpty() ? Collections.emptySet() : addedRelations;
      myRemovedFacts = removedFacts;
      myAddedFacts = addedFacts;
    }

    @Nullable
    static Change create(Set<Relation> removedRelations, Set<Relation> addedRelations, DfaFactMap removedFacts, DfaFactMap addedFacts) {
      if (removedRelations.isEmpty() && addedRelations.isEmpty() && removedFacts == DfaFactMap.EMPTY && addedFacts == DfaFactMap.EMPTY) {
        return null;
      }
      return new Change(removedRelations, addedRelations, removedFacts, addedFacts);
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
      DfaFactMap addedFacts = myAddedFacts.unite(other.myAddedFacts);
      DfaFactMap removedFacts = myRemovedFacts.unite(other.myRemovedFacts);
      return create(removed, added, removedFacts, addedFacts);
    }

    @Override
    public String toString() {
      String removed = StreamEx.of(myRemovedRelations).map(Object::toString).append(myRemovedFacts.toString())
        .without("").joining(", ");
      String added = StreamEx.of(myAddedRelations).map(Object::toString).append(myAddedFacts.toString())
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
    <T> FactDefinition<T> findFact(DfaValue value, DfaFactType<T> type) {
      if (value instanceof DfaVariableValue) {
        for (MemoryStateChange change = this; change != null; change = change.getPrevious()) {
          FactDefinition<T> factPair = factFromChange(type, change, change.myChanges.get(value));
          if (factPair != null) return factPair;
          factPair = factFromChange(type, change, change.myBridgeChanges.get(value));
          if (factPair != null) return factPair;
          if (change.myInstruction instanceof AssignInstruction && change.myTopOfStack == value && change.getPrevious() != null) {
            FactDefinition<T> fact = change.getPrevious().findFact(value, type);
            return new FactDefinition<>(change, fact.myFact);
          }
        }
        return new FactDefinition<>(null, ((DfaVariableValue)value).getInherentFacts().get(type));
      }
      return new FactDefinition<>(null, type.fromDfaValue(value));
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
    private static <T> FactDefinition<T> factFromChange(DfaFactType<T> type, MemoryStateChange change, Change varChange) {
      if (varChange != null) {
        T added = varChange.myAddedFacts.get(type);
        if (added != null) {
          return new FactDefinition<>(change, added); 
        }
        if (varChange.myRemovedFacts.get(type) != null) {
          return new FactDefinition<>(change, null);
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
          ((ExpressionPushingInstruction)myInstruction).getExpressionRange() == null) {
        return ((ExpressionPushingInstruction)myInstruction).getExpression();
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
      return new MemoryStateChange(new ArrayList<>(previous), new MergeInstruction(), Collections.emptyMap(), DfaUnknownValue.getInstance(),
                                   Collections.emptyMap());
    }

    MemoryStateChange withBridge(@NotNull Instruction instruction, @NotNull Map<DfaVariableValue, Change> bridge) {
      if (myInstruction != instruction) {
        if (instruction instanceof ConditionalGotoInstruction &&
            getExpression() == ((ConditionalGotoInstruction)instruction).getPsiAnchor()) {
          instruction = myInstruction;
        } else {
          return new MemoryStateChange(
            Collections.singletonList(this), instruction, Collections.emptyMap(), DfaUnknownValue.getInstance(), bridge);
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
      if (result.isEmpty() && value == DfaUnknownValue.getInstance()) {
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
    final @Nullable T myFact;

    FactDefinition(@Nullable MemoryStateChange change, @Nullable T fact) {
      myChange = change;
      myFact = fact;
    }

    @Nullable
    @Contract("!null -> !null")
    T getFact(T defaultFact) {
      return myFact == null ? defaultFact : myFact;
    }

    @Override
    public String toString() {
      return myFact + " @ " + myChange;
    }
  }
}
