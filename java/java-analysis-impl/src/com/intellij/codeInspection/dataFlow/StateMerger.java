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
import com.intellij.openapi.util.UnorderedPair;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
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
  private final Map<DfaMemoryStateImpl, Set<Fact>> myFacts = ContainerUtil.newIdentityHashMap();
  private final Map<DfaMemoryState, Map<DfaVariableValue, DfaMemoryStateImpl>> myCopyCache = ContainerUtil.newIdentityHashMap();

  @Nullable
  List<DfaMemoryStateImpl> mergeByFacts(@NotNull List<DfaMemoryStateImpl> states) {
    MultiMap<Fact, DfaMemoryStateImpl> statesByFact = MultiMap.createLinked();
    for (DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();
      for (Fact fact : getFacts(state)) {
        statesByFact.putValue(fact, state);
      }
    }

    for (final Fact fact : statesByFact.keySet()) {
      if (statesByFact.get(fact).size() == states.size() || fact.myPositive) continue;

      Collection<DfaMemoryStateImpl> statesWithNegations = statesByFact.get(fact.getPositiveCounterpart());
      if (statesWithNegations.isEmpty()) continue;

      ProgressManager.checkCanceled();

      MultiMap<Set<Fact>, DfaMemoryStateImpl> statesByUnrelatedFacts1 = mapByUnrelatedFacts(fact, statesByFact.get(fact));
      MultiMap<Set<Fact>, DfaMemoryStateImpl> statesByUnrelatedFacts2 = mapByUnrelatedFacts(fact, statesWithNegations);

      Replacements replacements = new Replacements(states);
      for (Set<Fact> key : statesByUnrelatedFacts1.keySet()) {
        final Collection<DfaMemoryStateImpl> group1 = statesByUnrelatedFacts1.get(key);
        final Collection<DfaMemoryStateImpl> group2 = statesByUnrelatedFacts2.get(key);
        if (group1.isEmpty() || group2.isEmpty()) continue;

        final Collection<DfaMemoryStateImpl> group = ContainerUtil.newArrayList(ContainerUtil.concat(group1, group2));
        
        final Set<DfaVariableValue> unknowns = getAllUnknownVariables(group);
        replacements.stripAndMerge(group, original -> {
          DfaMemoryStateImpl copy = withUnknownVariables(original, unknowns);
          fact.removeFromState(copy);
          if (fact.myType == FactType.equality) {
            restoreOtherInequalities(fact, group, copy);
          }
          return copy;
        });
      }

      if (replacements.hasMerges()) return replacements.getMergeResult();
    }
    return null;
  }

  @NotNull
  private MultiMap<Set<Fact>, DfaMemoryStateImpl> mapByUnrelatedFacts(@NotNull Fact fact,
                                                                      @NotNull Collection<DfaMemoryStateImpl> states1) {
    MultiMap<Set<Fact>, DfaMemoryStateImpl> statesByUnrelatedFacts1 = MultiMap.createLinked();
    for (DfaMemoryStateImpl state : states1) {
      statesByUnrelatedFacts1.putValue(getUnrelatedFacts(fact, state), state);
    }
    return statesByUnrelatedFacts1;
  }

  @NotNull
  private LinkedHashSet<Fact> getUnrelatedFacts(@NotNull final Fact fact, @NotNull DfaMemoryStateImpl state) {
    final LinkedHashSet<Fact> result = new LinkedHashSet<>();
    for (Fact other : getFacts(state)) {
      if (!fact.invalidatesFact(other)) {
        result.add(other);
      }
    }
    return result;
  }

  private void restoreOtherInequalities(@NotNull Fact removedFact, @NotNull Collection<DfaMemoryStateImpl> mergedGroup, @NotNull DfaMemoryStateImpl state) {
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
  private static Set<DfaConstValue> getOtherInequalities(@NotNull Fact removedFact, @NotNull Set<Fact> memberFacts, @NotNull DfaMemoryStateImpl state) {
    Set<DfaConstValue> otherInequalities = ContainerUtil.newLinkedHashSet();
    Set<DfaValue> eqValues = ContainerUtil.newHashSet(state.getEquivalentValues((DfaValue)removedFact.myArg));
    for (Fact candidate : memberFacts) {
      if (candidate.myType == FactType.equality && !candidate.myPositive && candidate.myVar == removedFact.myVar &&
          !eqValues.contains((DfaValue)candidate.myArg) &&
          candidate.myArg instanceof DfaConstValue) {
        otherInequalities.add((DfaConstValue)candidate.myArg);
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
      Map<DfaVariableValue, DfaVariableState> variableStates = state.getVariableStates();
      variableStates.forEach((varValue, varState) -> {
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
      final boolean myMerged;

      Record(DfaMemoryStateImpl state, LongRangeSet range, boolean merged) {
        myState = state;
        myRange = range;
        myMerged = merged;
      }

      Record union(Record other) {
        return new Record(myState, myRange.union(other.myRange), true);
      }

      DfaMemoryStateImpl getState() {
        if(myMerged) {
          myState.flushVariable(var);
          myState.setFact(var, RANGE, myRange);
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
      merged.merge(copyWithoutVar(state, var), new Record(state, range, false), Record::union);
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
    Set<Fact> result = myFacts.get(state);
    if (result != null) {
      return result;
    }
    
    result = ContainerUtil.newLinkedHashSet();

    IdentityHashMap<EqClass, EqClassInfo> classInfo = new IdentityHashMap<>();

    for (EqClass eqClass : state.getNonTrivialEqClasses()) {
      EqClassInfo info = classInfo.computeIfAbsent(eqClass, EqClassInfo::new);
      DfaValue constant = info.constant;
      List<DfaVariableValue> vars = info.vars;
      int size = vars.size();
      for (int i = 0; i < size; i++) {
        DfaVariableValue var = vars.get(i);
        if (constant != null) {
          result.add(Fact.createEqualityFact(var, constant, true));
        }
        for (int j = i + 1; j < size; j++) {
          DfaVariableValue eqVar = vars.get(j);
          result.add(Fact.createEqualityFact(var, eqVar, true));
        }
      }
    }

    for (UnorderedPair<EqClass> classPair : state.getDistinctClassPairs()) {
      EqClassInfo info1 = classInfo.computeIfAbsent(classPair.first, EqClassInfo::new);
      EqClassInfo info2 = classInfo.computeIfAbsent(classPair.second, EqClassInfo::new);

      for (DfaVariableValue var1 : info1.vars) {
        for (DfaVariableValue var2 : info2.vars) {
          result.add(new Fact(FactType.equality, var1, false, var2));
          result.add(new Fact(FactType.equality, var2, false, var1));
        }
      }
      if(info1.constant != null) {
        for (DfaVariableValue var2 : info2.vars) {
          result.add(new Fact(FactType.equality, var2, false, info1.constant));
        }
      }
      if(info2.constant != null) {
        for (DfaVariableValue var1 : info1.vars) {
          result.add(new Fact(FactType.equality, var1, false, info2.constant));
        }
      }
    }

    Map<DfaVariableValue, DfaVariableState> states = state.getVariableStates();
    for (Map.Entry<DfaVariableValue, DfaVariableState> entry : states.entrySet()) {
      DfaVariableValue var = entry.getKey();
      DfaVariableState variableState = entry.getValue();
      for (DfaPsiType type : variableState.getInstanceofValues()) {
        result.add(new Fact(FactType.instanceOf, var, true, type));
      }
      for (DfaPsiType type : variableState.getNotInstanceofValues()) {
        result.add(new Fact(FactType.instanceOf, var, false, type));
      }
    }

    myFacts.put(state, result);
    return result;
  }

  private enum FactType { equality, instanceOf }

  private static class Fact {
    @NotNull final FactType myType;
    @NotNull private final DfaVariableValue myVar;
    private final boolean myPositive;
    @NotNull
    private final Object myArg; // DfaValue for equality fact, DfaPsiType for instanceOf fact
    private final int myHash;

    private Fact(@NotNull FactType type, @NotNull DfaVariableValue var, boolean positive, @NotNull Object arg) {
      myType = type;
      myVar = var;
      myPositive = positive;
      myArg = arg;
      myHash = ((type.ordinal() * 31 + var.hashCode()) * 31 + arg.hashCode()) * 31 + (positive ? 1 : 0);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Fact)) return false;

      Fact fact = (Fact)o;

      if (myHash != fact.myHash) return false;
      if (myPositive != fact.myPositive) return false;
      if (myType != fact.myType) return false;
      if (!myArg.equals(fact.myArg)) return false;
      if (!myVar.equals(fact.myVar)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myHash;
    }

    @Override
    public String toString() {
      return myVar + " " + (myPositive ? "" : "!") + myType + " " + myArg;
    }

    @NotNull
    private static Fact createEqualityFact(@NotNull DfaVariableValue var, @NotNull DfaValue val, boolean equal) {
      if (val instanceof DfaVariableValue && val.getID() < var.getID()) {
        return new Fact(FactType.equality, (DfaVariableValue)val, equal, var);
      }
      return new Fact(FactType.equality, var, equal, val);
    }

    @NotNull
    private Fact getPositiveCounterpart() {
      return new Fact(myType, myVar, true, myArg);
    }

    boolean invalidatesFact(@NotNull Fact another) {
      if (another.myType != myType) return false;
      if (myType == FactType.equality) {
        return aboutSame(myVar, another.myVar) || aboutSame(myVar, another.myArg);
      }
      return aboutSame(myVar, another.myVar) && aboutSame(myArg, another.myArg);
    }
    
    private static boolean aboutSame(Object v1, Object v2) {
      return normalize(v1) == normalize(v2);
    }

    private static Object normalize(Object value) {
      if (value instanceof DfaVariableValue && ((DfaVariableValue)value).isNegated()) {
        return ((DfaVariableValue)value).createNegated();
      }
      return value;
    }

    void removeFromState(@NotNull DfaMemoryStateImpl state) {
      DfaVariableState varState = state.getVariableState(myVar);
      if (myType == FactType.equality) {
        state.flushVariable(myVar);
        state.setVariableState(myVar, varState);
      } else {
        state.setVariableState(myVar, varState.withoutType((DfaPsiType)myArg));
      }
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
