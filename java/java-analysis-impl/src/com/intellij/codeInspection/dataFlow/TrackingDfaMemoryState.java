// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.ConditionalGotoInstruction;
import com.intellij.codeInspection.dataFlow.instructions.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiExpression;
import com.intellij.util.ObjectUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class TrackingDfaMemoryState extends DfaMemoryStateImpl {
  private final List<MemoryStateChange> myHistory;

  protected TrackingDfaMemoryState(DfaValueFactory factory) {
    super(factory);
    myHistory = new ArrayList<>(1);
    myHistory.add(null);
  }

  protected TrackingDfaMemoryState(TrackingDfaMemoryState toCopy) {
    super(toCopy);
    myHistory = new ArrayList<>(toCopy.myHistory);
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
    for (MemoryStateChange change : ((TrackingDfaMemoryState)other).myHistory) {
      if (!tryMerge(change)) {
        myHistory.add(change);
      }
    }
  }

  private boolean tryMerge(MemoryStateChange change) {
    for (ListIterator<MemoryStateChange> iterator = myHistory.listIterator(); iterator.hasNext(); ) {
      MemoryStateChange myChange = iterator.next();
      MemoryStateChange merge = myChange.tryMerge(change);
      if (merge != null) {
        iterator.set(merge);
        return true;
      }
    }
    return false;
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
    Map<DfaVariableValue, Change> result = new HashMap<>();
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
        result.put(value, new Change(Collections.emptySet(), Collections.emptySet(), removed, added));
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
        result.compute(
          value, (v, change) -> change == null
                                ? Change.create(removed, added, DfaFactMap.EMPTY, DfaFactMap.EMPTY)
                                : Change.create(removed, added, change.myRemovedFacts, change.myAddedFacts));
      }
    }
    DfaValue value = isEmptyStack() ? DfaUnknownValue.getInstance() : peek();
    myHistory.replaceAll(prev -> MemoryStateChange.create(prev, instruction, result, value));
  }

  List<MemoryStateChange> getHistory() {
    return myHistory;
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

    static Change create(Set<Relation> removedRelations, Set<Relation> addedRelations, DfaFactMap removedFacts, DfaFactMap addedFacts) {
      if (removedRelations.isEmpty() && addedRelations.isEmpty() && removedFacts == DfaFactMap.EMPTY && addedFacts == DfaFactMap.EMPTY) {
        return null;
      }
      return new Change(removedRelations, addedRelations, removedFacts, addedFacts);
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
    final @Nullable MemoryStateChange myPrevious;
    final @NotNull Instruction myInstruction;
    final @NotNull Map<DfaVariableValue, Change> myChanges;
    final @NotNull DfaValue myTopOfStack;

    private MemoryStateChange(@Nullable MemoryStateChange previous,
                              @NotNull Instruction instruction,
                              @NotNull Map<DfaVariableValue, Change> changes,
                              @NotNull DfaValue topOfStack) {
      myPrevious = previous;
      myInstruction = instruction;
      myChanges = changes;
      myTopOfStack = topOfStack;
    }

    @Contract("null -> null")
    @Nullable
    MemoryStateChange findExpressionPush(@Nullable PsiExpression expression) {
      if (expression == null) return null;
      return findChange(change -> change.getExpression() == expression);
    }

    MemoryStateChange findRelation(DfaVariableValue value, @NotNull Predicate<Relation> relationPredicate) {
      return findChange(change -> {
        Change varChange = change.myChanges.get(value);
        return varChange != null && varChange.myAddedRelations.stream().anyMatch(relationPredicate);
      });
    }
    
    @NotNull
    <T> Pair<MemoryStateChange, T> findFact(DfaValue value, DfaFactType<T> type) {
      if (value instanceof DfaVariableValue) {
        for (MemoryStateChange change = this; change != null; change = change.myPrevious) {
          Change varChange = change.myChanges.get(value);
          if (varChange != null) {
            T added = varChange.myAddedFacts.get(type);
            if (added != null) {
              return Pair.create(change, added); 
            }
            if (varChange.myRemovedFacts.get(type) != null) {
              return Pair.create(change, null);
            }
          }
        }
        return Pair.create(null, ((DfaVariableValue)value).getInherentFacts().get(type));
      }
      return Pair.create(null, type.fromDfaValue(value));
    }

    @Nullable
    private MemoryStateChange findChange(@NotNull Predicate<MemoryStateChange> predicate) {
      for (MemoryStateChange change = myPrevious; change != null; change = change.myPrevious) {
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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MemoryStateChange change = (MemoryStateChange)o;
      return myInstruction.equals(change.myInstruction) &&
             myTopOfStack.equals(change.myTopOfStack) &&
             myChanges.equals(change.myChanges) &&
             Objects.equals(myPrevious, change.myPrevious);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myPrevious, myInstruction, myChanges, myTopOfStack);
    }

    @Nullable
    public MemoryStateChange tryMerge(MemoryStateChange change) {
      MemoryStateChange[] thisFlat = flatten();
      MemoryStateChange[] thatFlat = change.flatten();
      MemoryStateChange result = null;
      int thisIndex = 0, thatIndex = 0;
      while (true) {
        int curThis = thisIndex;
        if (thisIndex == thisFlat.length && thatIndex == thatFlat.length) {
          return result;
        }
        if (thisIndex == thisFlat.length || thatIndex == thatFlat.length) return null;
        while (thisIndex < thisFlat.length) {
          MemoryStateChange thisChange = thisFlat[thisIndex];
          if (thisChange.myInstruction == thatFlat[thatIndex].myInstruction) break;
          if (!thisChange.myChanges.isEmpty()) {
            thisIndex = thisFlat.length;
            break;
          }
          thisIndex++;
        }
        if (thisIndex == thisFlat.length) {
          thisIndex = curThis;
          while (thatIndex < thatFlat.length) {
            MemoryStateChange thatChange = thatFlat[thatIndex];
            if (thatChange.myInstruction == thisFlat[thisIndex].myInstruction) break;
            if (!thatChange.myChanges.isEmpty()) return null;
            thatIndex++;
          }
          if (thatIndex == thatFlat.length) return null;
        }
        MemoryStateChange thisChange = thisFlat[thisIndex];
        MemoryStateChange thatChange = thatFlat[thatIndex];
        if (thisChange == thatChange) {
          result = thisChange;
        } else {
          assert thisChange.myInstruction == thatChange.myInstruction;
          if (!thisChange.myChanges.equals(thatChange.myChanges)) return null;
          result = create(result, thisChange.myInstruction, thisChange.myChanges, thisChange.myTopOfStack.unite(thatChange.myTopOfStack));
        }
        thisIndex++;
        thatIndex++;
      }
    }

    @Nullable
    static MemoryStateChange create(@Nullable MemoryStateChange previous,
                                    @NotNull Instruction instruction,
                                    @NotNull Map<DfaVariableValue, Change> result,
                                    @NotNull DfaValue value) {
      if (result.isEmpty() && value == DfaUnknownValue.getInstance()) {
        return previous;
      }
      return new MemoryStateChange(previous, instruction, result, value);
    }

    MemoryStateChange[] flatten() {
      List<MemoryStateChange> changes = StreamEx.iterate(this, Objects::nonNull, change -> change.myPrevious).toList();
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
              "; Changes: " + EntryStream.of(myChanges).join(": ", "\n\t", "").joining());
    }
  }
}
