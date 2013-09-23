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
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UnorderedPair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
class StateMerger {
  private final Set<DfaMemoryStateImpl> myStates;
  private final MultiMap<UnorderedPair<DfaValue>,DfaMemoryStateImpl> myStatesByEq = new MultiMap<UnorderedPair<DfaValue>, DfaMemoryStateImpl>();
  private final Map<DfaMemoryStateImpl, Map<DfaVariableValue, DfaConstValue>> myVarValues = ContainerUtil.newIdentityHashMap();

  public StateMerger(Set<DfaMemoryStateImpl> states) {
    myStates = states;
    for (DfaMemoryStateImpl state : myStates) {
      ProgressManager.checkCanceled();
      
      Map<DfaVariableValue,DfaConstValue> varValues = ContainerUtil.newHashMap();
      for (UnorderedPair<DfaValue> pair : getEqPairs(state)) {
        myStatesByEq.putValue(pair, state);
        if (pair.first instanceof DfaVariableValue && pair.second instanceof DfaConstValue) {
          varValues.put((DfaVariableValue)pair.first, (DfaConstValue)pair.second);
        }
      }
      myVarValues.put(state, varValues);
    }
  }

  @Nullable
  public Set<DfaMemoryStateImpl> merge() {
    for (final DfaMemoryStateImpl state : myStates) {
      ProgressManager.checkCanceled();
      MultiMap<DfaVariableValue, DfaValue> distincts = getDistinctsMap(state);
      for (DfaVariableValue var : distincts.keySet()) {
        Map<DfaValue, Collection<DfaMemoryStateImpl>> statesByValue = getCompatibleStatesByValue(state, var, distincts);
        if (statesByValue == null) {
          continue;
        }

        DfaMemoryStateImpl copy = copyWithoutVar(state, var);
        
        final Set<DfaMemoryStateImpl> complementaryStates = findComplementaryStates(var, statesByValue, copy);
        if (complementaryStates == null) {
          continue;
        }

        complementaryStates.add(state);
        copy = copy.createCopy();
        for (DfaMemoryStateImpl removedState : complementaryStates) {
          for (DfaVariableValue unknownVar : removedState.getUnknownVariables()) {
            copy.doFlush(unknownVar, true);
          }
          if (removedState.isNull(var)) {
            copy.getVariableState(var).setNullable(true);
          }
        }

        Set<DfaMemoryStateImpl> result = ContainerUtil.newHashSet();
        result.add(copy);
        result.addAll(ContainerUtil.filter(myStates, new Condition<DfaMemoryStateImpl>() {
          @Override
          public boolean value(DfaMemoryStateImpl state) {
            return !complementaryStates.contains(state);
          }
        }));
        return result;
      }
      
    }
    return null;
  }

  private Map<Pair<DfaMemoryStateImpl, DfaVariableValue>, DfaMemoryStateImpl> myCopyCache = ContainerUtil.newHashMap();
  private DfaMemoryStateImpl copyWithoutVar(DfaMemoryStateImpl state, DfaVariableValue var) {
    Pair<DfaMemoryStateImpl, DfaVariableValue> key = Pair.create(state, var);
    DfaMemoryStateImpl copy = myCopyCache.get(key);
    if (copy == null) {
      copy = state.createCopy();
      copy.flushVariable(var);
      myCopyCache.put(key, copy);
    }
    return copy;
  }

  @Nullable
  private Set<DfaMemoryStateImpl> findComplementaryStates(DfaVariableValue var,
                                                                 Map<DfaValue, Collection<DfaMemoryStateImpl>> statesByValue,
                                                                 DfaMemoryStateImpl mainCopy) {
    Set<DfaMemoryStateImpl> removedStates = ContainerUtil.newTroveSet(ContainerUtil.<DfaMemoryStateImpl>identityStrategy());
  
  eachValue: 
    for (DfaValue value : statesByValue.keySet()) {
      for (DfaMemoryStateImpl originalState : statesByValue.get(value)) {
        if (mainCopy.equalsByRelations(copyWithoutVar(originalState, var))) {
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
                                                                                          MultiMap<DfaVariableValue, DfaValue> distincts) {
    Map<DfaValue, Collection<DfaMemoryStateImpl>> statesByValue = ContainerUtil.newHashMap();
    for (DfaValue value : distincts.get(var)) {
      List<DfaMemoryStateImpl> compatible = ContainerUtil.filter(myStatesByEq.get(createPair(var, value)), new Condition<DfaMemoryStateImpl>() {
        @Override
        public boolean value(DfaMemoryStateImpl state2) {
          return areCompatible(state, state2, var);
        }
      });
      if (compatible.isEmpty()) {
        return null;
      }
      statesByValue.put(value, compatible);
    }
    return statesByValue;
  }

  private boolean areCompatible(DfaMemoryStateImpl state1, DfaMemoryStateImpl state2, DfaVariableValue differentVar) {
    if (!state1.equalsSuperficially(state2)) {
      return false;
    }
    Map<DfaVariableValue, DfaConstValue> varValues1 = myVarValues.get(state1);
    Map<DfaVariableValue, DfaConstValue> varValues2 = myVarValues.get(state2);
    
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

  private static MultiMap<DfaVariableValue, DfaValue> getDistinctsMap(DfaMemoryStateImpl state) {
    MultiMap<DfaVariableValue, DfaValue> distincts = new MultiMap<DfaVariableValue, DfaValue>();
    for (Set<EqClass> classPair : state.getDistinctClassPairs()) {
      EqClass[] array = classPair.toArray(new EqClass[classPair.size()]);
      for (DfaValue value1 : array[0].getMemberValues()) {
        value1 = DfaMemoryStateImpl.unwrap(value1);
        for (DfaValue value2 : array[1].getMemberValues()) {
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

  private static List<UnorderedPair<DfaValue>> getEqPairs(DfaMemoryStateImpl state) {
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
    return ContainerUtil.newArrayList(eqPairs);
  }

  private static UnorderedPair<DfaValue> createPair(DfaVariableValue var, DfaValue val) {
    return new UnorderedPair<DfaValue>(var, val);
  }
}
