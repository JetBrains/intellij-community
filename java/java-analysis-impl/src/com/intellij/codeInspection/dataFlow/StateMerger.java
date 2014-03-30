/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
class StateMerger {
  private final Map<DfaMemoryStateImpl, LinkedHashSet<Fact>> myFacts = ContainerUtil.newIdentityHashMap();
  private final Map<DfaMemoryState, Map<DfaVariableValue, DfaMemoryStateImpl>> myCopyCache = ContainerUtil.newIdentityHashMap();

  @Nullable
  List<DfaMemoryStateImpl> mergeByFacts(List<DfaMemoryStateImpl> states) {
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
      
      MultiMap<Set<Fact>, DfaMemoryStateImpl> statesByUnrelatedFacts = MultiMap.createLinked();
      for (DfaMemoryStateImpl state : ContainerUtil.concat(statesByFact.get(fact), statesWithNegations)) {
        statesByUnrelatedFacts.putValue(getUnrelatedFacts(fact, state), state);
      }
      
      Set<DfaMemoryStateImpl> removedStates = ContainerUtil.newIdentityTroveSet();
      List<DfaMemoryStateImpl> result = ContainerUtil.newArrayList();
      for (Set<Fact> key : statesByUnrelatedFacts.keySet()) {
        Collection<DfaMemoryStateImpl> group = statesByUnrelatedFacts.get(key);
        if (group.size() > 1) {
          DfaMemoryStateImpl copy = group.iterator().next().createCopy();
          fact.removeFromState(copy);
          if (fact.myType == FactType.equality) {
            restoreOtherInequalities(fact, group, copy);
          }
          mergeUnknowns(copy, group);
          
          removedStates.addAll(group);
          result.add(copy);
        }
      }

      if (!result.isEmpty()) {
        for (DfaMemoryStateImpl state : states) {
          if (!removedStates.contains(state)) {
            result.add(state);
          }
        }
        return result;
      }
    }
    return null;
  }

  private LinkedHashSet<Fact> getUnrelatedFacts(final Fact fact, DfaMemoryStateImpl state) {
    return new LinkedHashSet<Fact>(ContainerUtil.filter(getFacts(state), new Condition<Fact>() {
      @Override
      public boolean value(Fact another) {
        return !fact.invalidatesFact(another);
      }
    }));
  }

  private void restoreOtherInequalities(Fact removedFact, Collection<DfaMemoryStateImpl> mergedGroup, DfaMemoryStateImpl state) {
    Set<DfaConstValue> inequalitiesToRestore = null;
    for (DfaMemoryStateImpl member : mergedGroup) {
      LinkedHashSet<Fact> memberFacts = getFacts(member);
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

  private static Set<DfaConstValue> getOtherInequalities(Fact removedFact, LinkedHashSet<Fact> memberFacts, DfaMemoryStateImpl state) {
    Set<DfaConstValue> otherInequalities = ContainerUtil.newLinkedHashSet();
    for (Fact candidate : memberFacts) {
      if (candidate.myType == FactType.equality && !candidate.myPositive && candidate.myVar == removedFact.myVar &&
          !state.areEquivalent((DfaValue)candidate.myArg, (DfaValue)removedFact.myArg) && 
          candidate.myArg instanceof DfaConstValue) {
        otherInequalities.add((DfaConstValue)candidate.myArg);
      }
    }
    return otherInequalities;
  }

  private static void mergeUnknowns(DfaMemoryStateImpl mergedState, Collection<DfaMemoryStateImpl> complementaryStates) {
    for (DfaMemoryStateImpl removedState : complementaryStates) {
      for (DfaVariableValue unknownVar : removedState.getUnknownVariables()) {
        mergedState.doFlush(unknownVar, true);
      }
    }
  }

  private static List<DfaMemoryStateImpl> getMergeResult(List<DfaMemoryStateImpl> statesBeforeMerge,
                                                         final THashSet<DfaMemoryStateImpl> mergedStates,
                                                         DfaMemoryStateImpl mergeResult) {
    List<DfaMemoryStateImpl> result = ContainerUtil.newArrayList();
    result.add(mergeResult);
    result.addAll(ContainerUtil.filter(statesBeforeMerge, new Condition<DfaMemoryStateImpl>() {
      @Override
      public boolean value(DfaMemoryStateImpl state) {
        return !mergedStates.contains(state);
      }
    }));
    return result;
  }

  @Nullable
  public List<DfaMemoryStateImpl> mergeByUnknowns(List<DfaMemoryStateImpl> states) {
    MultiMap<Integer, DfaMemoryStateImpl> byHash = new MultiMap<Integer, DfaMemoryStateImpl>();
    for (DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();
      byHash.putValue(state.getPartialHashCode(false, true), state);
    }

    for (Integer key : byHash.keySet()) {
      Collection<DfaMemoryStateImpl> similarStates = byHash.get(key);
      if (similarStates.size() < 2) continue;
      
      for (final DfaMemoryStateImpl state1 : similarStates) {
        ProgressManager.checkCanceled();
        List<DfaMemoryStateImpl> complementary = ContainerUtil.filter(similarStates, new Condition<DfaMemoryStateImpl>() {
          @Override
          public boolean value(DfaMemoryStateImpl state2) {
            return state1.equalsByRelations(state2) && state1.equalsByVariableStates(state2);
          }
        });
        if (complementary.size() > 1) {
          DfaMemoryStateImpl copy = state1.createCopy();
          mergeUnknowns(copy, complementary);
          return getMergeResult(states, ContainerUtil.newIdentityTroveSet(complementary), copy);
        }
      }
      
    }

    return null;
  }
  
  @Nullable
  public List<DfaMemoryStateImpl> mergeByNullability(List<DfaMemoryStateImpl> states) {
    MultiMap<Integer, DfaMemoryStateImpl> byHash = new MultiMap<Integer, DfaMemoryStateImpl>();
    for (DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();
      byHash.putValue(state.getPartialHashCode(false, false), state);
    }

    for (Integer key : byHash.keySet()) {
      Collection<DfaMemoryStateImpl> similarStates = byHash.get(key);
      if (similarStates.size() < 2) continue;
      
      for (final DfaMemoryStateImpl state1 : similarStates) {
        ProgressManager.checkCanceled();
        for (final DfaVariableValue var : state1.getChangedVariables()) {
          if (state1.getVariableState(var).getNullability() != Nullness.NULLABLE) {
            continue;
          }
          
          List<DfaMemoryStateImpl> complementary = ContainerUtil.filter(similarStates, new Condition<DfaMemoryStateImpl>() {
            @Override
            public boolean value(DfaMemoryStateImpl state2) {
              return state1.equalsByRelations(state2) && 
                     areEquivalentModuloVar(state1, state2, var) &&
                     areVarStatesEqualModuloNullability(state1, state2, var);
            }
          });
          if (complementary.size() > 1) {
            DfaMemoryStateImpl copy = state1.createCopy();
            mergeUnknowns(copy, complementary);
            return getMergeResult(states, ContainerUtil.newIdentityTroveSet(complementary), copy);
          }
        }

      }
      
    }

    return null;
  }
  
  private boolean areEquivalentModuloVar(DfaMemoryStateImpl state1, DfaMemoryStateImpl state2, DfaVariableValue var) {
    DfaMemoryStateImpl copy1 = copyWithoutVar(state1, var);
    DfaMemoryStateImpl copy2 = copyWithoutVar(state2, var);
    return copy2.equalsByRelations(copy1) && copy2.equalsByVariableStates(copy1);
  }

  private DfaMemoryStateImpl copyWithoutVar(DfaMemoryStateImpl state, DfaVariableValue var) {
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

  private static boolean areVarStatesEqualModuloNullability(DfaMemoryStateImpl state1, DfaMemoryStateImpl state2, DfaVariableValue var) {
    return state1.getVariableState(var).withNullability(Nullness.UNKNOWN).equals(state2.getVariableState(var).withNullability(Nullness.UNKNOWN));
  }

  private LinkedHashSet<Fact> getFacts(DfaMemoryStateImpl state) {
    LinkedHashSet<Fact> result = myFacts.get(state);
    if (result != null) {
      return result;
    }
    
    result = ContainerUtil.newLinkedHashSet();
    for (EqClass eqClass : state.getNonTrivialEqClasses()) {
      DfaConstValue constant = eqClass.findConstant(true);
      List<DfaVariableValue> vars = eqClass.getVariables();
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
      List<DfaVariableValue> vars1 = classPair.first.getVariables();
      List<DfaVariableValue> vars2 = classPair.second.getVariables();
      
      LinkedHashSet<DfaValue> firstSet = new LinkedHashSet<DfaValue>(vars1);
      ContainerUtil.addIfNotNull(firstSet, classPair.first.findConstant(true));

      LinkedHashSet<DfaValue> secondSet = new LinkedHashSet<DfaValue>(vars2);
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
    final FactType myType;
    final DfaVariableValue myVar;
    final boolean myPositive;
    final Object myArg; // DfaValue for equality fact, DfaPsiType for instanceOf fact

    private Fact(FactType type, DfaVariableValue var, boolean positive, Object arg) {
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

    static Fact createEqualityFact(DfaVariableValue var, DfaValue val, boolean equal) {
      if (val instanceof DfaVariableValue && val.getID() < var.getID()) {
        return new Fact(FactType.equality, (DfaVariableValue)val, equal, var);
      }
      return new Fact(FactType.equality, var, equal, val);
    }

    Fact getPositiveCounterpart() {
      return new Fact(myType, myVar, true, myArg);
    }

    boolean invalidatesFact(Fact another) {
      if (another.myType != myType) return false;
      if (myType == FactType.equality) {
        return myVar == another.myVar || myVar == another.myArg;
      }
      return myVar == another.myVar && myArg == another.myArg;
    }

    void removeFromState(DfaMemoryStateImpl state) {
      DfaVariableState varState = state.getVariableState(myVar);
      if (myType == FactType.equality) {
        state.flushVariable(myVar);
        state.setVariableState(myVar, varState);
      } else {
        state.setVariableState(myVar, varState.withoutType((DfaPsiType)myArg));
      }
    }
  }
}
