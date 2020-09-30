// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.LongStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
class StateMerger {
  private static final int COMPLEXITY_LIMIT = 250000;
  private final Map<DfaMemoryStateImpl, Set<Fact>> myFacts = new IdentityHashMap<>();
  private final Map<DfaMemoryState, Map<DfaVariableValue, DfaMemoryStateImpl>> myCopyCache = new IdentityHashMap<>();

  @Nullable
  List<DfaMemoryStateImpl> mergeByFacts(@NotNull List<DfaMemoryStateImpl> states) {
    MultiMap<Fact, DfaMemoryStateImpl> statesByFact = createFactToStateMap(states);
    Set<Fact> facts = statesByFact.keySet();

    int complexity = 0;

    for (final Fact fact : facts) {
      if (fact.myPositive) continue;
      Collection<DfaMemoryStateImpl> negativeStates = statesByFact.get(fact);
      if (negativeStates.size() == states.size()) continue;
      Collection<DfaMemoryStateImpl> positiveStates = statesByFact.get(fact.getPositiveCounterpart());
      if (positiveStates.isEmpty()) continue;

      ProgressManager.checkCanceled();

      MultiMap<CompactFactSet, DfaMemoryStateImpl> statesByUnrelatedFacts1 = mapByUnrelatedFacts(fact, negativeStates, facts);
      MultiMap<CompactFactSet, DfaMemoryStateImpl> statesByUnrelatedFacts2 = mapByUnrelatedFacts(fact, positiveStates, facts);

      complexity += StreamEx.of(statesByUnrelatedFacts1, statesByUnrelatedFacts2).flatCollection(MultiMap::keySet)
        .mapToInt(CompactFactSet::size).sum();
      if (complexity > COMPLEXITY_LIMIT) return null;

      Replacements replacements = new Replacements(states);
      for (Map.Entry<CompactFactSet, Collection<DfaMemoryStateImpl>> entry : statesByUnrelatedFacts1.entrySet()) {
        final Collection<DfaMemoryStateImpl> group1 = entry.getValue();
        final Collection<DfaMemoryStateImpl> group2 = statesByUnrelatedFacts2.get(entry.getKey());
        if (group1.isEmpty() || group2.isEmpty()) continue;

        final Collection<DfaMemoryStateImpl> group = ContainerUtil.newArrayList(ContainerUtil.concat(group1, group2));

        replacements.stripAndMerge(group, fact);
      }

      if (replacements.hasMerges()) return replacements.getMergeResult();
    }
    return null;
  }

  private @NotNull MultiMap<Fact, DfaMemoryStateImpl> createFactToStateMap(@NotNull List<DfaMemoryStateImpl> states) {
    MultiMap<Fact, DfaMemoryStateImpl> statesByFact = MultiMap.createLinked();
    Map<DfaTypeValue, Map<DfaVariableValue, Set<DfaMemoryStateImpl>>> constantVars = new HashMap<>();
    for (DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();
      for (Fact fact : getFacts(state)) {
        statesByFact.putValue(fact, state);
        DfaTypeValue value = fact.comparedToConstant();
        if (value != null) {
          constantVars.computeIfAbsent(value, k -> new HashMap<>())
            .computeIfAbsent(fact.myVar, k -> ContainerUtil.newIdentityTroveSet()).add(state);
        }
      }
    }

    for (final Fact fact : new ArrayList<>(statesByFact.keySet())) {
      if (fact.myPositive) continue;
      Collection<DfaMemoryStateImpl> negativeStates = statesByFact.get(fact);
      Collection<DfaMemoryStateImpl> positiveStates = statesByFact.get(fact.getPositiveCounterpart());
      if (isComparisonOfVariablesComparedWithConstant(fact, constantVars, positiveStates, negativeStates)) {
        statesByFact.remove(fact);
        statesByFact.remove(fact.getPositiveCounterpart());
      }
    }
    return statesByFact;
  }

  /**
   * Returns true if fact is {@link EqualityFact} which compares two variables, which both are known to be compared with
   * the same constant for all states. In this case the fact looks implied (like "a == null && b == null" implies "a == b")
   * and it's unnecessary to process it separately (processing "a == null" and "b == null" would be enough).
   *
   * @param fact fact to check
   * @param constantVars constant vars map
   * @param positiveStates states for which fact is positive
   * @param negativeStates states for which fact is negative
   * @return true if fact is {@link EqualityFact} which compares two variables which were compared with some constant
   */
  private static boolean isComparisonOfVariablesComparedWithConstant(Fact fact,
                                                                     Map<DfaTypeValue, Map<DfaVariableValue, Set<DfaMemoryStateImpl>>> constantVars,
                                                                     Collection<DfaMemoryStateImpl> positiveStates,
                                                                     Collection<DfaMemoryStateImpl> negativeStates) {
    if (!(fact instanceof EqualityFact) || !(((EqualityFact)fact).myArg instanceof DfaVariableValue)) return false;
    DfaVariableValue var1 = fact.myVar;
    DfaVariableValue var2 = (DfaVariableValue)((EqualityFact)fact).myArg;
    for (Map<DfaVariableValue, Set<DfaMemoryStateImpl>> map : constantVars.values()) {
      Set<DfaMemoryStateImpl> states1 = map.get(var1);
      Set<DfaMemoryStateImpl> states2 = map.get(var2);
      if (states1 != null && states2 != null &&
          states1.containsAll(negativeStates) && states1.containsAll(positiveStates) &&
          states2.containsAll(negativeStates) && states2.containsAll(positiveStates)) {
        return true;
      }
    }
    return false;
  }

  private @NotNull MultiMap<CompactFactSet, DfaMemoryStateImpl> mapByUnrelatedFacts(@NotNull Fact fact,
                                                                                    @NotNull Collection<DfaMemoryStateImpl> states,
                                                                                    @NotNull Set<Fact> interestingFacts) {
    MultiMap<CompactFactSet, DfaMemoryStateImpl> statesByUnrelatedFacts = MultiMap.createLinked();
    for (DfaMemoryStateImpl state : states) {
      statesByUnrelatedFacts.putValue(getUnrelatedFacts(fact, state, interestingFacts), state);
    }
    return statesByUnrelatedFacts;
  }

  private @NotNull CompactFactSet getUnrelatedFacts(final @NotNull Fact fact,
                                                    @NotNull DfaMemoryStateImpl state,
                                                    @NotNull Set<Fact> interestingFacts) {
    final ArrayList<Fact> result = new ArrayList<>();
    for (Fact other : getFacts(state)) {
      if (!fact.invalidatesFact(other) && interestingFacts.contains(other)) {
        result.add(other);
      }
    }
    return new CompactFactSet(state.getFactory(), result);
  }

  @Nullable
  List<DfaMemoryStateImpl> mergeByRanges(List<DfaMemoryStateImpl> states) {
    Map<DfaVariableValue, Set<LongRangeSet>> ranges = createRangeMap(states);
    boolean changed = false;
    // For every variable with more than one range, try to unite range info and see if some states could be merged after that
    for (Map.Entry<DfaVariableValue, Set<LongRangeSet>> entry : ranges.entrySet()) {
      if (entry.getValue().size() > 1) {
        List<DfaMemoryStateImpl> updated = mergeIndependentRanges(states, entry.getKey());
        if (updated != null) {
          states = updated;
          changed = true;
        }
      }
    }
    return changed ? states : null;
  }

  private static @NotNull Map<DfaVariableValue, Set<LongRangeSet>> createRangeMap(List<DfaMemoryStateImpl> states) {
    Map<DfaVariableValue, Set<LongRangeSet>> ranges = new LinkedHashMap<>();
    for (DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();
      state.forRecordedVariableTypes((varValue, dfType) -> {
        if (dfType instanceof DfIntegralType) {
          ranges.computeIfAbsent(varValue, k -> new HashSet<>()).add(((DfIntegralType)dfType).getRange());
        }
      });
    }
    return ranges;
  }

  private @Nullable List<DfaMemoryStateImpl> mergeIndependentRanges(List<DfaMemoryStateImpl> states, DfaVariableValue var) {
    ProgressManager.checkCanceled();
    Map<DfaMemoryStateImpl, List<DfaMemoryStateImpl>> merged = new LinkedHashMap<>();
    for (DfaMemoryStateImpl state : states) {
      DfType type = state.getDfType(var);
      if (!(type instanceof DfIntegralType)) return null;
      merged.computeIfAbsent(copyWithoutVar(state, var), k -> new ArrayList<>()).add(state);
    }
    if (merged.size() == states.size()) return null;
    return StreamEx.ofValues(merged).mapPartial(list -> list.stream().reduce((a, b) -> {
      assert a.getMergeabilityKey().equals(b.getMergeabilityKey());
      a.merge(b);
      return a;
    })).toList();
  }

  private @NotNull DfaMemoryStateImpl copyWithoutVar(@NotNull DfaMemoryStateImpl state, @NotNull DfaVariableValue var) {
    Map<DfaVariableValue, DfaMemoryStateImpl> map = myCopyCache.computeIfAbsent(state, k -> new IdentityHashMap<>());
    DfaMemoryStateImpl copy = map.get(var);
    if (copy == null) {
      copy = state.createCopy();
      copy.recordVariableType(var, var.getInherentType());
      copy.flushVariable(var);
      map.put(var, copy);
    }
    return copy;
  }

  private @NotNull Set<Fact> getFacts(@NotNull DfaMemoryStateImpl state) {
    return myFacts.computeIfAbsent(state, StateMerger::doGetFacts);
  }

  private static @NotNull Set<Fact> doGetFacts(DfaMemoryStateImpl state) {
    Set<Fact> result = new LinkedHashSet<>();

    for (EqClass eqClass : state.getNonTrivialEqClasses()) {
      int size = eqClass.size();
      for (int i = 0; i < size; i++) {
        DfaVariableValue var = eqClass.getVariable(i);
        for (int j = i + 1; j < size; j++) {
          DfaVariableValue eqVar = eqClass.getVariable(j);
          result.add(Fact.createEqualityFact(var, eqVar));
        }
      }
    }

    for (DistinctPairSet.DistinctPair classPair : state.getDistinctClassPairs()) {
      EqClass class1 = classPair.getFirst();
      EqClass class2 = classPair.getSecond();
      for (DfaVariableValue var1 : class1) {
        for (DfaVariableValue var2 : class2) {
          result.add(new EqualityFact(var1, false, var2));
          result.add(new EqualityFact(var2, false, var1));
        }
      }
    }

    DfaValueFactory factory = state.getFactory();
    state.forRecordedVariableTypes((var, dfType) -> {
      TypeConstraint typeConstraint = TypeConstraint.fromDfType(dfType);
      typeConstraint.instanceOfTypes().map(type -> new InstanceofFact(var, true, factory.fromDfType(type.asDfType()))).into(result);
      typeConstraint.notInstanceOfTypes().map(type -> new InstanceofFact(var, false, factory.fromDfType(type.asDfType()))).into(result);
      if (dfType instanceof DfConstantType) {
        result.add(new EqualityFact(var, true, var.getFactory().fromDfType(dfType)));
      }
      if (dfType instanceof DfAntiConstantType) {
        Set<?> notValues = ((DfAntiConstantType<?>)dfType).getNotValues();
        if (!notValues.isEmpty() && var.getType() != null) {
          for (Object notValue : notValues) {
            result.add(new EqualityFact(var, false, var.getFactory().fromDfType(DfTypes.constant(notValue, var.getType()))));
          }
        }
      }
    });
    return result;
  }

  static final class CompactFactSet {
    private final long[] myData;
    private final int myHashCode;
    private final DfaValueFactory myFactory;

    CompactFactSet(DfaValueFactory factory, Collection<Fact> facts) {
      myData = facts.stream().mapToLong(Fact::pack).toArray();
      Arrays.sort(myData);
      myHashCode = Arrays.hashCode(myData);
      myFactory = factory;
    }

    public int size() {
      return myData.length;
    }

    @Override
    public int hashCode() {
      return myHashCode;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (!(obj instanceof CompactFactSet)) return false;
      CompactFactSet other = (CompactFactSet)obj;
      return this.myHashCode == other.myHashCode && Arrays.equals(this.myData, other.myData);
    }

    @Override
    public String toString() {
      return LongStreamEx.of(myData).mapToObj(f -> Fact.unpack(myFactory, f)).joining(", ", "{", "}");
    }
  }

  abstract static class Fact {
    final boolean myPositive;
    final @NotNull DfaVariableValue myVar;
    private final int myHash;

    protected Fact(boolean positive, @NotNull DfaVariableValue var, int hash) {
      myPositive = positive;
      myVar = var;
      myHash = hash;
    }

    private int packLow() {
      return myPositive ? myVar.getID() : -myVar.getID();
    }

    abstract int packHigh();

    long pack() {
      int lo = packLow();
      int hi = packHigh();
      return ((long)hi << 32) | (lo & 0xFFFF_FFFFL);
    }

    @Override
    public final int hashCode() {
      return myHash;
    }

    abstract @NotNull Fact getPositiveCounterpart();

    DfaTypeValue comparedToConstant() {
      return null;
    }

    abstract boolean invalidatesFact(@NotNull Fact another);

    abstract void removeFromState(@NotNull DfaMemoryStateImpl state);

    void restoreCommonState(DfaMemoryStateImpl stripped, Collection<DfaMemoryStateImpl> merged) {
      DfType commonType = StreamEx.of(merged).map(s -> s.getDfType(myVar)).foldLeft(DfTypes.BOTTOM, DfType::join);
      stripped.meetDfType(myVar, commonType);
    }

    static @NotNull EqualityFact createEqualityFact(@NotNull DfaVariableValue var, @NotNull DfaValue val) {
      if (val instanceof DfaVariableValue && val.getID() < var.getID()) {
        return new EqualityFact((DfaVariableValue)val, true, var);
      }
      return new EqualityFact(var, true, val);
    }

    static Fact unpack(DfaValueFactory factory, long packed) {
      int lo = (int)(packed & 0xFFFF_FFFFL);
      int hi = (int)(packed >> 32);
      boolean positive = lo >= 0;
      DfaVariableValue var = (DfaVariableValue)factory.getValue(Math.abs(lo));
      if (hi >= 0) {
        return new EqualityFact(var, positive, factory.getValue(hi));
      } else {
        return new InstanceofFact(var, positive, (DfaTypeValue)factory.getValue(-hi));
      }
    }
  }

  static final class EqualityFact extends Fact {
    private final @NotNull DfaValue myArg;

    private EqualityFact(@NotNull DfaVariableValue var, boolean positive, @NotNull DfaValue arg) {
      super(positive, var, (var.hashCode() * 31 + arg.hashCode()) * 31 + (positive ? 1 : 0));
      myArg = arg;
    }

    @Override
    int packHigh() {
      return myArg.getID();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof EqualityFact)) return false;

      EqualityFact fact = (EqualityFact)o;
      return myArg == fact.myArg && myVar == fact.myVar && myPositive == fact.myPositive;
    }

    @Override
    public String toString() {
      return myVar + (myPositive ? " EQ " : " NE ") + myArg;
    }

    @Override
    DfaTypeValue comparedToConstant() {
      return myArg instanceof DfaTypeValue ? (DfaTypeValue)myArg : null;
    }

    @Override
    @NotNull
    EqualityFact getPositiveCounterpart() {
      return new EqualityFact(myVar, true, myArg);
    }

    @Override
    boolean invalidatesFact(@NotNull Fact another) {
      if (!(another instanceof EqualityFact)) return false;
      return myVar == another.myVar || myVar == ((EqualityFact)another).myArg;
    }

    @Override
    void removeFromState(@NotNull DfaMemoryStateImpl state) {
      DfType dfType = state.getDfType(myVar);
      if (dfType instanceof DfConstantType ||
          dfType instanceof DfAntiConstantType && ((DfAntiConstantType<?>)dfType).getNotValues().size() == 1) {
        state.flushVariable(myVar);
        if (myArg.getDfType() == DfTypes.NULL) {
          state.meetDfType(myVar, DfaNullability.NULLABLE.asDfType());
        }
      } else {
        state.removeEquivalence(myVar);
      }
    }
  }

  static final class InstanceofFact extends Fact {
    private final @NotNull DfaTypeValue myType;

    private InstanceofFact(@NotNull DfaVariableValue var, boolean positive, @NotNull DfaTypeValue type) {
      super(positive, var, (var.hashCode() * 31 + type.hashCode()) * 31 + (positive ? 1 : 0));
      myType = type;
    }

    @Override
    int packHigh() {
      return -myType.getID();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof InstanceofFact)) return false;

      InstanceofFact fact = (InstanceofFact)o;
      return myPositive == fact.myPositive && myType == fact.myType && myVar == fact.myVar;
    }

    @Override
    public String toString() {
      return myVar + (myPositive ? " IS " : " IS NOT ") + myType;
    }

    @Override
    @NotNull
    Fact getPositiveCounterpart() {
      return new InstanceofFact(myVar, true, myType);
    }

    @Override
    boolean invalidatesFact(@NotNull Fact another) {
      return another instanceof InstanceofFact &&
             myType == ((InstanceofFact)another).myType &&
             myVar == another.myVar;
    }

    @Override
    void removeFromState(@NotNull DfaMemoryStateImpl state) {
      DfType type = state.getDfType(myVar);
      if (type instanceof DfReferenceType) {
        state.recordVariableType(myVar, ((DfReferenceType)type).withoutType(TypeConstraint.fromDfType(myType.getDfType())));
      }
    }
  }

  private static final class Replacements {
    private final @NotNull List<DfaMemoryStateImpl> myAllStates;
    private final Set<DfaMemoryStateImpl> myRemovedStates = ContainerUtil.newIdentityTroveSet();
    private final List<DfaMemoryStateImpl> myMerged = new ArrayList<>();

    private Replacements(@NotNull List<DfaMemoryStateImpl> allStates) {
      myAllStates = allStates;
    }

    private boolean hasMerges() { return !myMerged.isEmpty(); }

    private @Nullable List<DfaMemoryStateImpl> getMergeResult() {
      if (hasMerges()) {
        List<DfaMemoryStateImpl> result = new ArrayList<>(myMerged);
        for (DfaMemoryStateImpl state : myAllStates) {
          if (!myRemovedStates.contains(state)) {
            result.add(state);
          }
        }
        return result;
      }
      return null;
    }

    private void stripAndMerge(@NotNull Collection<DfaMemoryStateImpl> group, @NotNull Fact fact) {
      if (group.size() <= 1) return;

      MultiMap<DfaMemoryStateImpl, DfaMemoryStateImpl> strippedToOriginals = MultiMap.create();
      for (DfaMemoryStateImpl original : group) {
        DfaMemoryStateImpl copy = original.createCopy();
        fact.removeFromState(copy);
        strippedToOriginals.putValue(copy, original);
      }
      for (Map.Entry<DfaMemoryStateImpl, Collection<DfaMemoryStateImpl>> entry : strippedToOriginals.entrySet()) {
        Collection<DfaMemoryStateImpl> merged = entry.getValue();
        if (merged.size() > 1) {
          DfaMemoryStateImpl stripped = entry.getKey();
          fact.restoreCommonState(stripped, merged);
          for (DfaMemoryStateImpl state : merged) {
            stripped.afterMerge(state);
          }
          myRemovedStates.addAll(merged);
          myMerged.add(stripped);
        }
      }
    }
  }
}
