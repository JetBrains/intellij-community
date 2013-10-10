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

import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaPsiType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UnorderedPair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
class StateMerger {
  private final Map<DfaMemoryStateImpl, Map<DfaVariableValue, DfaConstValue>> myVarValues = ContainerUtil.newIdentityHashMap();
  private final Map<DfaMemoryStateImpl, List<UnorderedPair<DfaValue>>> myEqPairs = ContainerUtil.newIdentityHashMap();
  private final Map<DfaMemoryState, Map<DfaVariableValue, DfaMemoryStateImpl>> myCopyCache = ContainerUtil.newIdentityHashMap();

  @Nullable
  public List<DfaMemoryStateImpl> mergeByEquality(List<DfaMemoryStateImpl> states) {
    final MultiMap<UnorderedPair<DfaValue>,DfaMemoryStateImpl> statesByEq = new MultiMap<UnorderedPair<DfaValue>, DfaMemoryStateImpl>();
    for (DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();
      for (UnorderedPair<DfaValue> pair : getEqPairs(state)) {
        statesByEq.putValue(pair, state);
      }
    }

    for (final DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();
      MultiMap<DfaVariableValue, DfaValue> distincts = getDistinctsMap(state);
      for (DfaVariableValue var : distincts.keySet()) {
        Map<DfaValue, Collection<DfaMemoryStateImpl>> statesByValue = getCompatibleStatesByValue(state, var, distincts, statesByEq);
        if (statesByValue == null) {
          continue;
        }

        final THashSet<DfaMemoryStateImpl> complementaryStates = findComplementaryStates(var, statesByValue, state);
        if (complementaryStates == null) {
          continue;
        }

        DfaMemoryStateImpl copy = copyWithoutVar(state, var).createCopy();

        complementaryStates.add(state);
        mergeNullableState(var, copy, complementaryStates);
        mergeUnknowns(copy, complementaryStates);
        return getMergeResult(states, complementaryStates, copy);
      }
      
    }
    return null;
  }

  private static void mergeUnknowns(DfaMemoryStateImpl mergedState, Collection<DfaMemoryStateImpl> complementaryStates) {
    for (DfaMemoryStateImpl removedState : complementaryStates) {
      for (DfaVariableValue unknownVar : removedState.getUnknownVariables()) {
        mergedState.doFlush(unknownVar, true);
      }
    }
  }
  private static void mergeNullableState(DfaVariableValue var,
                                             DfaMemoryStateImpl mergedState,
                                             Collection<DfaMemoryStateImpl> complementaryStates) {
    for (DfaMemoryStateImpl removedState : complementaryStates) {
      if (removedState.getVariableState(var).isNullable()) {
        mergedState.setVariableState(var, mergedState.getVariableState(var).withNullability(Nullness.NULLABLE));
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
            return state1.equalsSuperficially(state2) && state1.equalsByRelations(state2) && state1.equalsByVariableStates(state2);
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
              return state1.equalsSuperficially(state2) && 
                     state1.equalsByRelations(state2) && 
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
  
  @Nullable
  public List<DfaMemoryStateImpl> mergeByType(List<DfaMemoryStateImpl> states) {
    MultiMap<Pair<DfaVariableValue, DfaPsiType>,DfaMemoryStateImpl> byInstanceof = new MultiMap<Pair<DfaVariableValue, DfaPsiType>, DfaMemoryStateImpl>();
    for (final DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();
      for (DfaVariableValue value : state.getChangedVariables()) {
        for (DfaPsiType instanceofValue : state.getVariableState(value).myInstanceofValues) {
          byInstanceof.putValue(Pair.create(value, instanceofValue), state);
        }
      }
    }
    
    for (final DfaMemoryStateImpl state : states) {
      ProgressManager.checkCanceled();

      for (final DfaVariableValue var : state.getChangedVariables()) {
        for (final DfaPsiType notInstanceof : state.getVariableState(var).myNotInstanceofValues) {
          final DfaVariableState varStateWithoutType = getVarStateWithoutType(state, var, notInstanceof);
          List<DfaMemoryStateImpl> complementaryStates = ContainerUtil.filter(
            byInstanceof.get(Pair.create(var, notInstanceof)),
            new Condition<DfaMemoryStateImpl>() {
              @Override
              public boolean value(DfaMemoryStateImpl another) {
                return seemCompatible(state, another, var) &&
                       another.getVariableState(var).myInstanceofValues.contains(notInstanceof) &&
                       varStateWithoutType.equals(getVarStateWithoutType(another, var, notInstanceof)) &&
                       areEquivalentModuloVar(another, state, var) && 
                       !(state.isNull(var) && another.isNotNull(var));
              }
            });
          if (complementaryStates.isEmpty()) {
            continue;
          }

          DfaMemoryStateImpl copy = state.createCopy();
          copy.setVariableState(var, varStateWithoutType);
          
          complementaryStates.add(state);
          mergeNullableState(var, copy, complementaryStates);
          mergeUnknowns(copy, complementaryStates);
          return getMergeResult(states, ContainerUtil.newIdentityTroveSet(complementaryStates), copy);
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

  private static DfaVariableState getVarStateWithoutType(DfaMemoryStateImpl s, DfaVariableValue var, DfaPsiType type) {
    return s.getVariableState(var).withoutType(type).withNullability(Nullness.UNKNOWN);
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

  @Nullable
  private THashSet<DfaMemoryStateImpl> findComplementaryStates(DfaVariableValue var,
                                                                 Map<DfaValue, Collection<DfaMemoryStateImpl>> statesByValue,
                                                                 DfaMemoryStateImpl state) {
    THashSet<DfaMemoryStateImpl> removedStates = ContainerUtil.newIdentityTroveSet();
  
  eachValue: 
    for (DfaValue value : statesByValue.keySet()) {
      for (DfaMemoryStateImpl originalState : statesByValue.get(value)) {
        if (areEquivalentModuloVar(originalState, state, var)) {
          removedStates.add(originalState);
          continue eachValue;
        }
      }
      return null;
    }
    return removedStates;
  }

  @Nullable
  private Map<DfaValue, Collection<DfaMemoryStateImpl>> getCompatibleStatesByValue(final DfaMemoryStateImpl state,
                                                                                          final DfaVariableValue var,
                                                                                          MultiMap<DfaVariableValue, DfaValue> distincts,
                                                                                          MultiMap<UnorderedPair<DfaValue>,DfaMemoryStateImpl> statesByEq) {
    Map<DfaValue, Collection<DfaMemoryStateImpl>> statesByValue = ContainerUtil.newHashMap();
    for (DfaValue value : distincts.get(var)) {
      List<DfaMemoryStateImpl> compatible = ContainerUtil.filter(statesByEq.get(createPair(var, value)), new Condition<DfaMemoryStateImpl>() {
        @Override
        public boolean value(DfaMemoryStateImpl state2) {
          return seemCompatible(state, state2, var) &&
                 areVarStatesEqualModuloNullability(state, state2, var);
        }
      });
      if (compatible.isEmpty()) {
        return null;
      }
      statesByValue.put(value, compatible);
    }
    return statesByValue;
  }

  private static boolean areVarStatesEqualModuloNullability(DfaMemoryStateImpl state1, DfaMemoryStateImpl state2, DfaVariableValue var) {
    return state1.getVariableState(var).withNullability(Nullness.UNKNOWN).equals(state2.getVariableState(var).withNullability(Nullness.UNKNOWN));
  }

  private boolean seemCompatible(DfaMemoryStateImpl state1, DfaMemoryStateImpl state2, DfaVariableValue differentVar) {
    if (!state1.equalsSuperficially(state2)) {
      return false;
    }
    Map<DfaVariableValue, DfaConstValue> varValues1 = getVarValues(state1);
    Map<DfaVariableValue, DfaConstValue> varValues2 = getVarValues(state2);
    
    for (DfaVariableValue var : varValues1.keySet()) {
      if (var != differentVar && varValues1.get(var) != varValues2.get(var)) {
        return false;
      }
    }
    for (DfaVariableValue var : varValues2.keySet()) {
      if (var != differentVar && !varValues1.containsKey(var)) {
        return false;
      }
    }
    return true;
  }

  private Map<DfaVariableValue, DfaConstValue> getVarValues(DfaMemoryStateImpl state) {
    Map<DfaVariableValue, DfaConstValue> map = myVarValues.get(state);
    if (map == null) {
      map = ContainerUtil.newHashMap();
      for (UnorderedPair<DfaValue> pair : getEqPairs(state)) {
        if (pair.first instanceof DfaVariableValue && pair.second instanceof DfaConstValue) {
          map.put((DfaVariableValue)pair.first, (DfaConstValue)pair.second);
        }
      }
      myVarValues.put(state, map);

    }
    return map;
  }

  private static MultiMap<DfaVariableValue, DfaValue> getDistinctsMap(DfaMemoryStateImpl state) {
    MultiMap<DfaVariableValue, DfaValue> distincts = new MultiMap<DfaVariableValue, DfaValue>();
    for (UnorderedPair<EqClass> classPair : state.getDistinctClassPairs()) {
      for (DfaValue value1 : classPair.first.getMemberValues()) {
        value1 = DfaMemoryStateImpl.unwrap(value1);
        for (DfaValue value2 : classPair.second.getMemberValues()) {
          value2 = DfaMemoryStateImpl.unwrap(value2);
          if (value1 instanceof DfaVariableValue) {
            if (value2 instanceof DfaVariableValue || value2 instanceof DfaConstValue) {
              distincts.putValue((DfaVariableValue)value1, value2);
            }
          }
          if (value2 instanceof DfaVariableValue) {
            if (value1 instanceof DfaVariableValue || value1 instanceof DfaConstValue) {
              distincts.putValue((DfaVariableValue)value2, value1);
            }
          }
        }
      }
    }
    return distincts;
  }

  private List<UnorderedPair<DfaValue>> getEqPairs(DfaMemoryStateImpl state) {
    List<UnorderedPair<DfaValue>> result = myEqPairs.get(state);
    if (result == null) {
      Set<UnorderedPair<DfaValue>> eqPairs = ContainerUtil.newHashSet();
      for (EqClass eqClass : state.getNonTrivialEqClasses()) {
        DfaConstValue constant = eqClass.findConstant(true);
        List<DfaVariableValue> vars = eqClass.getVariables();
        for (int i = 0; i < vars.size(); i++) {
          DfaVariableValue var = vars.get(i);
          if (constant != null) {
            eqPairs.add(createPair(var, constant));
          }
          for (int j = i + 1; j < vars.size(); j++) {
            eqPairs.add(createPair(var, vars.get(j)));
          }
        }
      }
      myEqPairs.put(state, result = ContainerUtil.newArrayList(eqPairs));
    }
    return result;
  }

  private static UnorderedPair<DfaValue> createPair(DfaVariableValue var, DfaValue val) {
    return new UnorderedPair<DfaValue>(var, val);
  }
}
