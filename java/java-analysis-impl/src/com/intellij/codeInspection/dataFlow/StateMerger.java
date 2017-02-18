/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.UnorderedPair;
import com.intellij.psi.JavaTokenType;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
class StateMerger {
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
    return new LinkedHashSet<>(ContainerUtil.filter(getFacts(state), another -> !fact.invalidatesFact(another)));
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
        state.applyCondition(relationFactory.createRelation(removedFact.myVar, toRestore, JavaTokenType.EQEQ, true));
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
    Map<DfaVariableValue, DfaMemoryStateImpl> map = myCopyCache.get(state);
    if (map == null) {
      myCopyCache.put(state, map = ContainerUtil.newIdentityHashMap());
    }
    DfaMemoryStateImpl copy = map.get(var);
    if (copy == null) {
      copy = state.createCopy();
      copy.flushVariable(var);
      map.put(var, copy);
    }
    return copy;
  }

  private static boolean areVarStatesEqualModuloNullability(@NotNull DfaMemoryStateImpl state1, @NotNull DfaMemoryStateImpl state2, @NotNull DfaVariableValue var) {
    return state1.getVariableState(var).withNullability(Nullness.UNKNOWN).equals(state2.getVariableState(var).withNullability(Nullness.UNKNOWN));
  }

  @NotNull
  private Set<Fact> getFacts(@NotNull DfaMemoryStateImpl state) {
    Set<Fact> result = myFacts.get(state);
    if (result != null) {
      return result;
    }
    
    result = ContainerUtil.newLinkedHashSet();
    for (EqClass eqClass : state.getNonTrivialEqClasses()) {
      DfaValue constant = eqClass.findConstant(true);
      List<DfaVariableValue> vars = eqClass.getVariables(false);
      for (DfaVariableValue var : vars) {
        if (constant != null) {
          result.add(Fact.createEqualityFact(var, constant, true));
        }
        for (DfaVariableValue eqVar : vars) {
          if (var != eqVar) {
            result.add(Fact.createEqualityFact(var, eqVar, true));
          }
        }
      }
    }
    
    for (UnorderedPair<EqClass> classPair : state.getDistinctClassPairs()) {
      List<DfaVariableValue> vars1 = classPair.first.getVariables(false);
      List<DfaVariableValue> vars2 = classPair.second.getVariables(false);
      
      LinkedHashSet<DfaValue> firstSet = new LinkedHashSet<>(vars1);
      ContainerUtil.addIfNotNull(firstSet, classPair.first.findConstant(true));

      LinkedHashSet<DfaValue> secondSet = new LinkedHashSet<>(vars2);
      ContainerUtil.addIfNotNull(secondSet, classPair.second.findConstant(true));

      for (DfaVariableValue var : vars1) {
        for (DfaValue value : secondSet) {
          result.add(new Fact(FactType.equality, var, false, value));
        }
      }
      for (DfaVariableValue var : vars2) {
        for (DfaValue value : firstSet) {
          result.add(new Fact(FactType.equality, var, false, value));
        }
      }
    }

    Map<DfaVariableValue, DfaVariableState> states = state.getVariableStates();
    for (DfaVariableValue var : states.keySet()) {
      DfaVariableState variableState = states.get(var);
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

    private Fact(@NotNull FactType type, @NotNull DfaVariableValue var, boolean positive, @NotNull Object arg) {
      myType = type;
      myVar = var;
      myPositive = positive;
      myArg = arg;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Fact)) return false;

      Fact fact = (Fact)o;

      if (myPositive != fact.myPositive) return false;
      if (!myArg.equals(fact.myArg)) return false;
      if (myType != fact.myType) return false;
      if (!myVar.equals(fact.myVar)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myType.hashCode();
      result = 31 * result + myVar.hashCode();
      result = 31 * result + (myPositive ? 1 : 0);
      result = 31 * result + myArg.hashCode();
      return result;
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

}
