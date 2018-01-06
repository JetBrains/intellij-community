/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.LongStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.dataFlow.DfaFactType.CAN_BE_NULL;
import static com.intellij.codeInspection.dataFlow.DfaFactType.RANGE;

/**
 * @author peter
 */
class StateMerger {
  public static final int MAX_RANGE_STATES = 100;
  private static final int COMPLEXITY_LIMIT = 250000;
  private final Map<DfaMemoryStateImpl, Set<Fact>> myFacts = ContainerUtil.newIdentityHashMap();
  private final Map<DfaMemoryState, Map<DfaVariableValue, DfaMemoryStateImpl>> myCopyCache = ContainerUtil.newIdentityHashMap();

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
        
        final Set<DfaVariableValue> unknowns = getAllUnknownVariables(group);
        replacements.stripAndMerge(group, original -> {
          DfaMemoryStateImpl copy = withUnknownVariables(original, unknowns);
          fact.removeFromState(copy);
          if (fact instanceof EqualityFact) {
            restoreOtherInequalities((EqualityFact)fact, group, copy);
          }
          return copy;
        });
      }

      if (replacements.hasMerges()) return replacements.getMergeResult();
    }
    return null;
  }

  @NotNull
  private MultiMap<Fact, DfaMemoryStateImpl> createFactToStateMap(@NotNull List<DfaMemoryStateImpl> states) {
    MultiMap<Fact, DfaMemoryStateImpl> statesByFact = MultiMap.createLinked();
    Map<DfaConstValue, Map<DfaVariableValue, Set<DfaMemoryStateImpl>>> constantVars = new HashMap<>();
    for (DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();
      for (Fact fact : getFacts(state)) {
        statesByFact.putValue(fact, state);
        DfaConstValue value = fact.comparedToConstant();
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
                                                                     Map<DfaConstValue, Map<DfaVariableValue, Set<DfaMemoryStateImpl>>> constantVars,
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

  @NotNull
  private MultiMap<CompactFactSet, DfaMemoryStateImpl> mapByUnrelatedFacts(@NotNull Fact fact,
                                                                      @NotNull Collection<DfaMemoryStateImpl> states,
                                                                      @NotNull Set<Fact> interestingFacts) {
    MultiMap<CompactFactSet, DfaMemoryStateImpl> statesByUnrelatedFacts = MultiMap.createLinked();
    for (DfaMemoryStateImpl state : states) {
      statesByUnrelatedFacts.putValue(getUnrelatedFacts(fact, state, interestingFacts), state);
    }
    return statesByUnrelatedFacts;
  }

  @NotNull
  private CompactFactSet getUnrelatedFacts(@NotNull final Fact fact,
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

  private void restoreOtherInequalities(@NotNull EqualityFact removedFact,
                                        @NotNull Collection<DfaMemoryStateImpl> mergedGroup,
                                        @NotNull DfaMemoryStateImpl state) {
    Set<DfaConstValue> inequalitiesToRestore = null;
    for (DfaMemoryStateImpl member : mergedGroup) {
      Set<Fact> memberFacts = getFacts(member);
      if (memberFacts.contains(removedFact)) {
        Set<DfaConstValue> otherInequalities = getOtherInequalities(removedFact, memberFacts, member);
        if (inequalitiesToRestore == null) {
          inequalitiesToRestore = otherInequalities;
        } else {
          inequalitiesToRestore.retainAll(otherInequalities);
        }
      }
    }
    if (inequalitiesToRestore != null) {
      DfaRelationValue.Factory relationFactory = state.getFactory().getRelationFactory();
      for (DfaConstValue toRestore : inequalitiesToRestore) {
        state.applyCondition(relationFactory.createRelation(removedFact.myVar, RelationType.NE, toRestore));
      }
    }
  }

  @NotNull
  private static Set<DfaConstValue> getOtherInequalities(@NotNull EqualityFact removedFact,
                                                         @NotNull Set<Fact> memberFacts,
                                                         @NotNull DfaMemoryStateImpl state) {
    Set<DfaConstValue> otherInequalities = ContainerUtil.newLinkedHashSet();
    Set<DfaValue> eqValues = ContainerUtil.newHashSet(state.getEquivalentValues(removedFact.myArg));
    for (Fact candidate : memberFacts) {
      if (!(candidate instanceof EqualityFact)) continue;
      EqualityFact equality = (EqualityFact)candidate;
      if (!equality.myPositive &&
          equality.myVar == removedFact.myVar &&
          equality.myArg instanceof DfaConstValue &&
          !eqValues.contains(equality.myArg)) {
        otherInequalities.add((DfaConstValue)equality.myArg);
      }
    }
    return otherInequalities;
  }

  @NotNull
  private static Set<DfaVariableValue> getAllUnknownVariables(@NotNull Collection<DfaMemoryStateImpl> complementary) {
    final Set<DfaVariableValue> toFlush = ContainerUtil.newLinkedHashSet();
    for (DfaMemoryStateImpl removedState : complementary) {
      toFlush.addAll(removedState.getUnknownVariables());
    }
    return toFlush;
  }

  @NotNull
  private static DfaMemoryStateImpl withUnknownVariables(@NotNull DfaMemoryStateImpl original, @NotNull Set<DfaVariableValue> toFlush) {
    DfaMemoryStateImpl copy = original.createCopy();
    for (DfaVariableValue value : toFlush) {
      copy.doFlush(value, true);
    }
    return copy;
  }

  @Nullable
  List<DfaMemoryStateImpl> mergeByUnknowns(@NotNull List<DfaMemoryStateImpl> states) {
    MultiMap<Integer, DfaMemoryStateImpl> byHash = new MultiMap<>();
    for (DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();
      byHash.putValue(state.getPartialHashCode(false, true), state);
    }

    Replacements replacements = new Replacements(states);
    for (Integer key : byHash.keySet()) {
      Collection<DfaMemoryStateImpl> similarStates = byHash.get(key);
      if (similarStates.size() < 2) continue;
      
      for (final DfaMemoryStateImpl state1 : similarStates) {
        ProgressManager.checkCanceled();
        List<DfaMemoryStateImpl> complementary = ContainerUtil.filter(similarStates, state2 -> state1.equalsByRelations(state2) && state1.equalsByVariableStates(state2));
        if (mergeUnknowns(replacements, complementary)) break;
      }
    }

    return replacements.getMergeResult();
  }
  
  @Nullable
  List<DfaMemoryStateImpl> mergeByNullability(List<DfaMemoryStateImpl> states) {
    MultiMap<Integer, DfaMemoryStateImpl> byHash = new MultiMap<>();
    for (DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();
      byHash.putValue(state.getPartialHashCode(false, false), state);
    }

    Replacements replacements = new Replacements(states);
    for (Integer key : byHash.keySet()) {
      Collection<DfaMemoryStateImpl> similarStates = byHash.get(key);
      if (similarStates.size() < 2) continue;
      
      groupLoop:
      for (final DfaMemoryStateImpl state1 : similarStates) {
        ProgressManager.checkCanceled();
        for (final DfaVariableValue var : state1.getChangedVariables()) {
          if (state1.getVariableState(var).getNullability() != Nullness.NULLABLE) {
            continue;
          }
          
          List<DfaMemoryStateImpl> complementary = ContainerUtil.filter(similarStates, state2 -> state1.equalsByRelations(state2) &&
                                                                                             areEquivalentModuloVar(state1, state2, var) &&
                                                                                             areVarStatesEqualModuloNullability(state1, state2, var));
          if (mergeUnknowns(replacements, complementary)) break groupLoop;
        }
      }
    }

    return replacements.getMergeResult();
  }

  @Nullable
  List<DfaMemoryStateImpl> mergeByRanges(List<DfaMemoryStateImpl> states) {
    // If the same variable has different range A and B in different memState and range A contains range B
    // then range A is replaced with range B
    Map<DfaVariableValue, Map<LongRangeSet, LongRangeSet>> ranges = createRangeMap(states);
    boolean changed = false;
    for (Map<LongRangeSet, LongRangeSet> map : ranges.values()) {
      for (Map.Entry<LongRangeSet, LongRangeSet> entry : map.entrySet()) {
        for(LongRangeSet candidate : map.values()) {
          if(!entry.getValue().equals(candidate) && candidate.contains(entry.getValue())) {
            entry.setValue(candidate);
            changed = true;
          }
        }
      }
    }
    if(changed) {
      changed = false;
      for (DfaMemoryStateImpl state : states) {
        for (Map.Entry<DfaVariableValue, Map<LongRangeSet, LongRangeSet>> entry : ranges.entrySet()) {
          DfaVariableState variableState = state.getVariableState(entry.getKey());
          LongRangeSet range = variableState.getFact(RANGE);
          if (range != null && !range.isEmpty() && range.max() == range.min()) continue;
          LongRangeSet boundingRange = entry.getValue().get(range);
          if (boundingRange != null && !boundingRange.equals(range)) {
            state.setFact(entry.getKey(), RANGE, boundingRange);
            changed = true;
          }
        }
      }
      if(changed) {
        return new ArrayList<>(new LinkedHashSet<>(states));
      }
    }
    List<DfaMemoryStateImpl> merged = mergeIndependentRanges(states, ranges);
    if(merged != null) return merged;
    return dropExcessRangeInfo(states, ranges.keySet());
  }

  @NotNull
  private static Map<DfaVariableValue, Map<LongRangeSet, LongRangeSet>> createRangeMap(List<DfaMemoryStateImpl> states) {
    Map<DfaVariableValue, Map<LongRangeSet, LongRangeSet>> ranges = new LinkedHashMap<>();
    for (DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();
      state.forVariableStates((varValue, varState) -> {
        LongRangeSet range = varState.getFact(RANGE);
        if (range != null) {
          ranges.computeIfAbsent(varValue, k -> new HashMap<>()).put(range, range);
        }
      });
    }
    return ranges;
  }

  @Nullable
  private List<DfaMemoryStateImpl> mergeIndependentRanges(List<DfaMemoryStateImpl> states, Map<DfaVariableValue, Map<LongRangeSet, LongRangeSet>> ranges) {
    boolean changed = false;
    // For every variable with more than one range, try to union range info and see if some states could be merged after that
    for (Map.Entry<DfaVariableValue, Map<LongRangeSet, LongRangeSet>> entry : ranges.entrySet()) {
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

  @Nullable
  private List<DfaMemoryStateImpl> mergeIndependentRanges(List<DfaMemoryStateImpl> states, DfaVariableValue var) {
    class Record {
      final DfaMemoryStateImpl myState;
      final LongRangeSet myRange;
      final Set<EqualityFact> myCommonEqualities;

      Record(DfaMemoryStateImpl state, LongRangeSet range, Set<EqualityFact> commonEqualities) {
        myState = state;
        myRange = range;
        myCommonEqualities = commonEqualities;
      }

      Set<EqualityFact> getEqualityFacts() {
        return StreamEx.of(getFacts(myState)).select(EqualityFact.class)
          .filter(fact -> fact.myVar == var || fact.myArg == var).toSet();
      }

      Record union(Record other) {
        Set<EqualityFact> equalities = myCommonEqualities == null ? getEqualityFacts() : myCommonEqualities;
        equalities.retainAll(other.getEqualityFacts());
        return new Record(myState, myRange.union(other.myRange), equalities);
      }

      DfaMemoryStateImpl getState() {
        if(myCommonEqualities != null) {
          myState.flushVariable(var);
          myState.setFact(var, RANGE, myRange);
          for (EqualityFact equality : myCommonEqualities) {
            equality.applyTo(myState);
          }
        }
        return myState;
      }
    }

    ProgressManager.checkCanceled();
    Map<DfaMemoryStateImpl, Record> merged = new LinkedHashMap<>();
    for (DfaMemoryStateImpl state : states) {
      DfaVariableState variableState = state.getVariableState(var);
      LongRangeSet range = variableState.getFact(RANGE);
      if (range == null) {
        range = LongRangeSet.fromType(var.getVariableType());
        if (range == null) return null;
      }
      merged.merge(copyWithoutVar(state, var), new Record(state, range, null), Record::union);
    }
    return merged.size() == states.size() ? null : StreamEx.ofValues(merged).map(Record::getState).toList();
  }

  @Nullable
  private static List<DfaMemoryStateImpl> dropExcessRangeInfo(List<DfaMemoryStateImpl> states, Set<DfaVariableValue> rangeVariables) {
    if (states.size() <= MAX_RANGE_STATES || rangeVariables.isEmpty()) return null;
    // If there are too many states, try to drop range information from some variable
    DfaVariableValue lastVar = Collections.max(rangeVariables, Comparator.comparingInt(DfaVariableValue::getID));
    for (DfaMemoryStateImpl state : states) {
      state.setFact(lastVar, RANGE, null);
    }
    return new ArrayList<>(new HashSet<>(states));
  }

  private static boolean mergeUnknowns(@NotNull Replacements replacements, @NotNull List<DfaMemoryStateImpl> complementary) {
    if (complementary.size() < 2) return false;

    final Set<DfaVariableValue> toFlush = getAllUnknownVariables(complementary);
    if (toFlush.isEmpty()) return false;

    return replacements.stripAndMerge(complementary, original -> withUnknownVariables(original, toFlush));
  }

  private boolean areEquivalentModuloVar(@NotNull DfaMemoryStateImpl state1, @NotNull DfaMemoryStateImpl state2, @NotNull DfaVariableValue var) {
    DfaMemoryStateImpl copy1 = copyWithoutVar(state1, var);
    DfaMemoryStateImpl copy2 = copyWithoutVar(state2, var);
    return copy2.equalsByRelations(copy1) && copy2.equalsByVariableStates(copy1);
  }

  @NotNull
  private DfaMemoryStateImpl copyWithoutVar(@NotNull DfaMemoryStateImpl state, @NotNull DfaVariableValue var) {
    Map<DfaVariableValue, DfaMemoryStateImpl> map = myCopyCache.computeIfAbsent(state, k -> ContainerUtil.newIdentityHashMap());
    DfaMemoryStateImpl copy = map.get(var);
    if (copy == null) {
      copy = state.createCopy();
      copy.flushVariable(var);
      map.put(var, copy);
    }
    return copy;
  }

  private static boolean areVarStatesEqualModuloNullability(@NotNull DfaMemoryStateImpl state1,
                                                            @NotNull DfaMemoryStateImpl state2,
                                                            @NotNull DfaVariableValue var) {
    return state1.getVariableState(var).withoutFact(CAN_BE_NULL).equals(state2.getVariableState(var).withoutFact(CAN_BE_NULL));
  }

  @NotNull
  private Set<Fact> getFacts(@NotNull DfaMemoryStateImpl state) {
    return myFacts.computeIfAbsent(state, StateMerger::doGetFacts);
  }

  @NotNull
  private static Set<Fact> doGetFacts(DfaMemoryStateImpl state) {
    Set<Fact> result = ContainerUtil.newLinkedHashSet();

    IdentityHashMap<EqClass, EqClassInfo> classInfo = new IdentityHashMap<>();

    for (EqClass eqClass : state.getNonTrivialEqClasses()) {
      EqClassInfo info = classInfo.computeIfAbsent(eqClass, EqClassInfo::new);
      DfaValue constant = info.constant;
      List<DfaVariableValue> vars = info.vars;
      int size = vars.size();
      for (int i = 0; i < size; i++) {
        DfaVariableValue var = vars.get(i);
        if (constant != null) {
          result.add(Fact.createEqualityFact(var, constant));
        }
        for (int j = i + 1; j < size; j++) {
          DfaVariableValue eqVar = vars.get(j);
          result.add(Fact.createEqualityFact(var, eqVar));
        }
      }
    }

    for (DistinctPairSet.DistinctPair classPair : state.getDistinctClassPairs()) {
      EqClassInfo info1 = classInfo.computeIfAbsent(classPair.getFirst(), EqClassInfo::new);
      EqClassInfo info2 = classInfo.computeIfAbsent(classPair.getSecond(), EqClassInfo::new);

      for (DfaVariableValue var1 : info1.vars) {
        for (DfaVariableValue var2 : info2.vars) {
          result.add(new EqualityFact(var1, false, var2));
          result.add(new EqualityFact(var2, false, var1));
        }
      }
      if(info1.constant != null) {
        for (DfaVariableValue var2 : info2.vars) {
          result.add(new EqualityFact(var2, false, info1.constant));
        }
      }
      if(info2.constant != null) {
        for (DfaVariableValue var1 : info1.vars) {
          result.add(new EqualityFact(var1, false, info2.constant));
        }
      }
    }

    state.forVariableStates((var, variableState) -> {
      TypeConstraint typeConstraint = variableState.getTypeConstraint();
      for (DfaPsiType type : typeConstraint.getInstanceofValues()) {
        result.add(new InstanceofFact(var, true, type));
      }
      for (DfaPsiType type : typeConstraint.getNotInstanceofValues()) {
        result.add(new InstanceofFact(var, false, type));
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

  static abstract class Fact {
    final boolean myPositive;
    @NotNull final DfaVariableValue myVar;
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

    @NotNull
    abstract Fact getPositiveCounterpart();

    DfaConstValue comparedToConstant() {
      return null;
    }

    abstract boolean invalidatesFact(@NotNull Fact another);

    abstract void removeFromState(@NotNull DfaMemoryStateImpl state);

    @NotNull
    static EqualityFact createEqualityFact(@NotNull DfaVariableValue var, @NotNull DfaValue val) {
      if (val instanceof DfaVariableValue && val.getID() < var.getID()) {
        return new EqualityFact((DfaVariableValue)val, true, var);
      }
      return new EqualityFact(var, true, val);
    }

    static DfaValue normalize(DfaValue value) {
      if (value instanceof DfaVariableValue && ((DfaVariableValue)value).isNegated()) {
        return ((DfaVariableValue)value).createNegated();
      }
      return value;
    }

    static Fact unpack(DfaValueFactory factory, long packed) {
      int lo = (int)(packed & 0xFFFF_FFFFL);
      int hi = (int)(packed >> 32);
      boolean positive = lo >= 0;
      DfaVariableValue var = (DfaVariableValue)factory.getValue(Math.abs(lo));
      if (hi >= 0) {
        return new EqualityFact(var, positive, factory.getValue(hi));
      } else {
        return new InstanceofFact(var, positive, factory.getType(-hi));
      }
    }
  }

  static final class EqualityFact extends Fact {
    @NotNull private final DfaValue myArg;

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
    DfaConstValue comparedToConstant() {
      return myArg instanceof DfaConstValue ? (DfaConstValue)myArg : null;
    }

    @Override
    @NotNull
    EqualityFact getPositiveCounterpart() {
      return new EqualityFact(myVar, true, myArg);
    }

    void applyTo(DfaMemoryStateImpl state) {
      state.applyCondition(state.getFactory().createCondition(myVar, myPositive ? RelationType.EQ : RelationType.NE, myArg));
    }

    @Override
    boolean invalidatesFact(@NotNull Fact another) {
      if (!(another instanceof EqualityFact)) return false;
      DfaValue normalizedVar = normalize(myVar);
      return normalizedVar == normalize(another.myVar) || normalizedVar == normalize(((EqualityFact)another).myArg);
    }

    @Override
    void removeFromState(@NotNull DfaMemoryStateImpl state) {
      state.removeEquivalenceRelations(myVar);
    }
  }

  static final class InstanceofFact extends Fact {
    @NotNull private final DfaPsiType myType;

    private InstanceofFact(@NotNull DfaVariableValue var, boolean positive, @NotNull DfaPsiType type) {
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
             normalize(myVar) == normalize(another.myVar);
    }

    @Override
    void removeFromState(@NotNull DfaMemoryStateImpl state) {
      DfaVariableState varState = state.getVariableState(myVar);
      state.setVariableState(myVar, varState.withoutType(myType));
    }
  }

  private static class Replacements {
    @NotNull private final List<DfaMemoryStateImpl> myAllStates;
    private final Set<DfaMemoryStateImpl> myRemovedStates = ContainerUtil.newIdentityTroveSet();
    private final List<DfaMemoryStateImpl> myMerged = ContainerUtil.newArrayList();

    private Replacements(@NotNull List<DfaMemoryStateImpl> allStates) {
      myAllStates = allStates;
    }

    private boolean hasMerges() { return !myMerged.isEmpty(); }

    @Nullable
    private List<DfaMemoryStateImpl> getMergeResult() {
      if (hasMerges()) {
        List<DfaMemoryStateImpl> result = ContainerUtil.newArrayList(myMerged);
        for (DfaMemoryStateImpl state : myAllStates) {
          if (!myRemovedStates.contains(state)) {
            result.add(state);
          }
        }
        return result;
      }
      return null;
    }

    private boolean stripAndMerge(@NotNull Collection<DfaMemoryStateImpl> group,
                                  @NotNull Function<DfaMemoryStateImpl, DfaMemoryStateImpl> stripper) {
      if (group.size() <= 1) return false;

      MultiMap<DfaMemoryStateImpl, DfaMemoryStateImpl> strippedToOriginals = MultiMap.create();
      for (DfaMemoryStateImpl original : group) {
        strippedToOriginals.putValue(stripper.fun(original), original);
      }
      boolean hasMerges = false;
      for (Map.Entry<DfaMemoryStateImpl, Collection<DfaMemoryStateImpl>> entry : strippedToOriginals.entrySet()) {
        Collection<DfaMemoryStateImpl> merged = entry.getValue();
        if (merged.size() > 1) {
          myRemovedStates.addAll(merged);
          myMerged.add(entry.getKey());
          hasMerges = true;
        }
      }
      return hasMerges;
    }
  }

  static final class EqClassInfo {
    final List<DfaVariableValue> vars;
    final DfaValue constant;

    EqClassInfo(EqClass eqClass) {
      vars = eqClass.getVariables(false);
      constant = eqClass.findConstant(true);
    }
  }
}
