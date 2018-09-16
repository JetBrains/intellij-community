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

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Invariant: qualifiers of the variables used in myEqClasses or myVariableStates must be canonical variables
 * where canonical variable is the minimal DfaVariableValue inside its eqClass, according to EqClass#CANONICAL_VARIABLE_COMPARATOR.
 */
public class DfaMemoryStateImpl implements DfaMemoryState {
  private static final Logger LOG = Logger.getInstance(DfaMemoryStateImpl.class);

  private final DfaValueFactory myFactory;

  private final List<EqClass> myEqClasses;
  // dfa value id -> indices in myEqClasses list of the classes which contain the id
  private final MyIdMap myIdToEqClassesIndices;
  private final Stack<DfaValue> myStack;
  private final DistinctPairSet myDistinctClasses;
  private final LinkedHashMap<DfaVariableValue,DfaVariableState> myVariableStates;
  private final Map<DfaVariableValue,DfaVariableState> myDefaultVariableStates;
  private boolean myEphemeral;

  protected DfaMemoryStateImpl(final DfaValueFactory factory) {
    myFactory = factory;
    myDefaultVariableStates = ContainerUtil.newTroveMap();
    myEqClasses = ContainerUtil.newArrayList();
    myVariableStates = ContainerUtil.newLinkedHashMap();
    myDistinctClasses = new DistinctPairSet(this);
    myStack = new Stack<>();
    myIdToEqClassesIndices = new MyIdMap();
  }

  protected DfaMemoryStateImpl(DfaMemoryStateImpl toCopy) {
    myFactory = toCopy.myFactory;
    myEphemeral = toCopy.myEphemeral;
    myDefaultVariableStates = toCopy.myDefaultVariableStates; // shared between all states

    myStack = new Stack<>(toCopy.myStack);
    myDistinctClasses = new DistinctPairSet(this, toCopy.myDistinctClasses);

    myEqClasses = ContainerUtil.newArrayList(toCopy.myEqClasses);
    myIdToEqClassesIndices = (MyIdMap)toCopy.myIdToEqClassesIndices.clone();
    myVariableStates = ContainerUtil.newLinkedHashMap(toCopy.myVariableStates);

    myCachedNonTrivialEqClasses = toCopy.myCachedNonTrivialEqClasses;
    myCachedHash = toCopy.myCachedHash;
  }

  @NotNull
  public DfaValueFactory getFactory() {
    return myFactory;
  }

  @NotNull
  @Override
  public DfaMemoryStateImpl createCopy() {
    return new DfaMemoryStateImpl(this);
  }

  @NotNull
  @Override
  public DfaMemoryStateImpl createClosureState() {
    DfaMemoryStateImpl copy = createCopy();
    for (DfaVariableValue value : new ArrayList<>(copy.myVariableStates.keySet())) {
      copy.dropFact(value, DfaFactType.LOCALITY);
    }
    copy.flushFields();
    copy.emptyStack();
    return copy;
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof DfaMemoryStateImpl)) return false;
    DfaMemoryStateImpl that = (DfaMemoryStateImpl)obj;
    if (myCachedHash != null && that.myCachedHash != null && !myCachedHash.equals(that.myCachedHash)) return false;
    return myEphemeral == that.myEphemeral && myStack.equals(that.myStack) &&
           getNonTrivialEqClasses().equals(that.getNonTrivialEqClasses()) &&
           getDistinctClassPairs().equals(that.getDistinctClassPairs()) &&
           myVariableStates.equals(that.myVariableStates);
  }

  Object getSuperficialKey() {
    return Pair.create(myEphemeral, myStack);
  }

  DistinctPairSet getDistinctClassPairs() {
    return myDistinctClasses;
  }

  private LinkedHashSet<EqClass> myCachedNonTrivialEqClasses;
  LinkedHashSet<EqClass> getNonTrivialEqClasses() {
    if (myCachedNonTrivialEqClasses != null) return myCachedNonTrivialEqClasses;

    LinkedHashSet<EqClass> result = ContainerUtil.newLinkedHashSet();
    for (EqClass eqClass : myEqClasses) {
      if (eqClass != null && eqClass.size() > 1) {
        result.add(eqClass);
      }
    }
    return myCachedNonTrivialEqClasses = result;
  }

  private Integer myCachedHash;
  public int hashCode() {
    if (myCachedHash != null) return myCachedHash;

    int hash = ((getNonTrivialEqClasses().hashCode() * 31 +
                 getDistinctClassPairs().hashCode()) * 31 +
                 myStack.hashCode()) * 31 + myVariableStates.hashCode();
    return myCachedHash = hash;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append('<');
    if (myEphemeral) {
      result.append("ephemeral, ");
    }

    for (EqClass set : getNonTrivialEqClasses()) {
      result.append(set);
    }

    if (!myDistinctClasses.isEmpty()) {
      result.append("\n  distincts: ");
      String distincts = StreamEx.of(getDistinctClassPairs()).map(DistinctPairSet.DistinctPair::toString).sorted().joining(" ");
      result.append(distincts);
    }

    if (!myStack.isEmpty()) {
      result.append("\n  stack: ").append(StringUtil.join(myStack, ","));
    }
    if (!myVariableStates.isEmpty()) {
      result.append("\n  vars: ");
      myVariableStates.forEach((key, value) -> result.append("[").append(key).append("->").append(value).append("] "));
    }
    result.append('>');
    return result.toString();
  }

  @NotNull
  @Override
  public DfaValue pop() {
    myCachedHash = null;
    return myStack.pop();
  }

  @NotNull
  @Override
  public DfaValue peek() {
    return myStack.peek();
  }

  @Nullable
  @Override
  public DfaValue getStackValue(int offset) {
    int index = myStack.size() - 1 - offset;
    return index < 0 ? null : myStack.get(index);
  }

  @Override
  public void push(@NotNull DfaValue value) {
    myCachedHash = null;
    myStack.push(value);
  }

  @Override
  public void emptyStack() {
    while (!myStack.isEmpty() && !(myStack.peek() instanceof DfaControlTransferValue)) {
      myCachedHash = null;
      myStack.pop();
    }
  }

  @Override
  public void setVarValue(DfaVariableValue var, DfaValue value) {
    if (var == value) return;

    value = handleStackValueOnVariableFlush(value, var, null);
    flushVariable(var);
    flushQualifiedMethods(var);

    if (value instanceof DfaUnknownValue) {
      setVariableState(var, getVariableState(var).withNotNull());
      return;
    }

    DfaVariableState state = getVariableState(var).withValue(value);
    if (value instanceof DfaFactMapValue) {
      setVariableState(var, state.withFacts(((DfaFactMapValue)value).getFacts()));
    }
    else {
      setVariableState(var, isNull(value) ? state.withFact(DfaFactType.NULLABILITY, DfaNullability.NULLABLE) : state);
      DfaRelationValue dfaEqual = myFactory.getRelationFactory().createRelation(var, RelationType.EQ, value);
      if (dfaEqual == null) return;
      applyCondition(dfaEqual);

      if (value instanceof DfaVariableValue) {
        setVariableState(var, getVariableState((DfaVariableValue)value));
      }
    }

    updateEqClassesByState(var);
  }

  private DfaValue handleStackValueOnVariableFlush(DfaValue value,
                                                   DfaVariableValue flushed,
                                                   DfaVariableValue replacement) {
    if (value instanceof DfaVariableValue && (value == flushed || flushed.getDependentVariables().contains(value))) {
      if (replacement != null) {
        DfaVariableValue target = replaceQualifier((DfaVariableValue)value, flushed, replacement);
        if (target != value) return target;
      }
      DfaNullability dfaNullability = isNotNull(value) ? DfaNullability.NOT_NULL : getValueFact(value, DfaFactType.NULLABILITY);
      if (dfaNullability == null) {
        dfaNullability = DfaNullability.fromNullability(((DfaVariableValue)value).getInherentNullability());
      }
      return myFactory.withFact(myFactory.createTypeValue(value.getType(), Nullability.UNKNOWN), DfaFactType.NULLABILITY, dfaNullability);
    }
    return value;
  }

  @Nullable("for non-variables and non-constants which can't be compared by ==")
  private Integer getOrCreateEqClassIndex(@NotNull DfaValue dfaValue) {
    int i = getEqClassIndex(dfaValue);
    if (i != -1) return i;
    if (!canBeInRelation(dfaValue)) return null;
    dfaValue = canonicalize(dfaValue);
    int freeIndex = myEqClasses.indexOf(null);
    int resultIndex = freeIndex >= 0 ? freeIndex : myEqClasses.size();
    EqClass aClass = new EqClass(myFactory);
    aClass.add(dfaValue.getID());

    if (freeIndex >= 0) {
      myEqClasses.set(freeIndex, aClass);
    }
    else {
      myEqClasses.add(aClass);
    }
    myIdToEqClassesIndices.put(dfaValue.getID(), resultIndex);
    checkInvariants();

    return resultIndex;
  }

  /**
   * Returns true if current state describes all possible concrete program states described by {@code that} state.
   *
   * @param that a sub-state candidate
   * @return true if current state is a super-state of the supplied state.
   */
  public boolean isSuperStateOf(DfaMemoryStateImpl that) {
    if (myEphemeral && !that.myEphemeral) return false;
    if (myStack.size() != that.myStack.size()) return false;
    for (int i = 0; i < myStack.size(); i++) {
      if (!isSuperValue(myStack.get(i), that.myStack.get(i))) return false;
    }
    if (!that.getDistinctClassPairs().containsAll(getDistinctClassPairs())) return false;
    Set<EqClass> thisClasses = this.getNonTrivialEqClasses();
    Set<EqClass> thatClasses = that.getNonTrivialEqClasses();
    if(!thisClasses.equals(thatClasses)) {
      // If any two values are equivalent in this, they also must be equivalent in that
      for (EqClass thisClass: thisClasses) {
        if (thatClasses.stream().noneMatch(thatClass -> thisClass.forEach(thatClass::contains))) {
          return false;
        }
      }
    }
    Set<DfaVariableValue> values = new HashSet<>(this.myVariableStates.keySet());
    values.addAll(that.myVariableStates.keySet());
    for (DfaVariableValue value : values) {
      // the default variable state is not always a superstate for any non-default state
      // (e.g. default can be nullable, but current state can be notnull)
      // so we cannot limit checking to myVariableStates map only
      DfaVariableState thisState = this.getVariableState(value);
      DfaVariableState thatState = that.getVariableState(value);
      if(!thisState.isSuperStateOf(thatState)) return false;
    }
    return true;
  }

  private static boolean isSuperValue(DfaValue superValue, DfaValue subValue) {
    if (superValue == DfaUnknownValue.getInstance() || superValue == subValue) return true;
    if (superValue instanceof DfaFactMapValue && subValue instanceof DfaFactMapValue) {
      return ((DfaFactMapValue)superValue).getFacts().isSuperStateOf(((DfaFactMapValue)subValue).getFacts());
    }
    return false;
  }

  private static boolean canBeInRelation(@NotNull DfaValue dfaValue) {
    DfaValue unwrapped = unwrap(dfaValue);
    return unwrapped instanceof DfaVariableValue || unwrapped instanceof DfaConstValue;
  }

  @NotNull
  List<DfaValue> getEquivalentValues(@NotNull DfaValue dfaValue) {
    EqClass set = getEqClass(dfaValue);
    return set == null ? Collections.emptyList() : set.getMemberValues();
  }

  private boolean canBeNaN(@NotNull DfaValue dfaValue) {
    if (!(dfaValue instanceof DfaVariableValue) ||
        (!PsiType.FLOAT.equals(dfaValue.getType()) && !PsiType.DOUBLE.equals(dfaValue.getType()))) {
      return false;
    }
    EqClass eqClass = getEqClass(dfaValue);
    DfaValue constant = eqClass == null ? null : eqClass.findConstant(false);
    return constant == null || isNaN(constant);
  }


  private boolean isEffectivelyNaN(@NotNull DfaValue dfaValue) {
    EqClass eqClass = getEqClass(dfaValue);
    return eqClass != null && isNaN(eqClass.findConstant(false));
  }

  List<EqClass> getEqClasses() {
    return myEqClasses;
  }

  @Nullable
  private EqClass getEqClass(DfaValue value) {
    int index = getEqClassIndex(value);
    return index == -1 ? null : myEqClasses.get(index);
  }

  /**
   * Returns existing equivalence class index or -1 if not found
   * @param dfaValue value to find a class for
   * @return class index or -1 if not found
   */
  int getEqClassIndex(@NotNull DfaValue dfaValue) {
    Integer classIndex = myIdToEqClassesIndices.get(dfaValue.getID());
    if (classIndex == null) {
      dfaValue = canonicalize(dfaValue);
      classIndex = myIdToEqClassesIndices.get(dfaValue.getID());
    }

    if (classIndex == null) return -1;

    EqClass aClass = myEqClasses.get(classIndex);
    assert aClass.contains(dfaValue.getID());
    return classIndex;
  }

  DfaVariableValue getCanonicalVariable(DfaValue val) {
    EqClass eqClass = getEqClass(val);
    return eqClass == null ? null : eqClass.getCanonicalVariable();
  }

  /**
   * Unite equivalence classes containing given values
   *
   * @param val1 the first value
   * @param val2 the second value
   * @return true if classes were successfully united.
   */
  private boolean uniteClasses(DfaValue val1, DfaValue val2) {
    DfaVariableValue var1 = getCanonicalVariable(val1);
    DfaVariableValue var2 = getCanonicalVariable(val2);
    Integer c1Index = getOrCreateEqClassIndex(val1);
    Integer c2Index = getOrCreateEqClassIndex(val2);
    if (c1Index == null || c2Index == null || c1Index.equals(c2Index)) return true;

    if (!myDistinctClasses.unite(c1Index, c2Index)) return false;

    EqClass c1 = myEqClasses.get(c1Index);
    EqClass c2 = myEqClasses.get(c2Index);

    if (c1.findConstant(true) != null && c2.findConstant(true) != null) return false;

    EqClass newClass = new EqClass(c1);

    myEqClasses.set(c1Index, newClass);
    for (int i = 0; i < c2.size(); i++) {
      int c = c2.get(i);
      newClass.add(c);
      myIdToEqClassesIndices.remove(c);
      myIdToEqClassesIndices.put(c, c1Index);
    }

    myEqClasses.set(c2Index, null);
    checkInvariants();

    if (var1 == null || var2 == null || var1 == var2) return true;
    int compare = EqClass.CANONICAL_VARIABLE_COMPARATOR.compare(var1, var2);
    return compare < 0 ? convertQualifiers(var2, var1) : convertQualifiers(var1, var2);
  }

  private static DfaVariableValue replaceQualifier(DfaVariableValue variable, DfaVariableValue from, DfaVariableValue to) {
    DfaVariableValue qualifier = variable.getQualifier();
    if (qualifier != null) {
      return variable.withQualifier(replaceQualifier(qualifier == from ? to : qualifier, from, to));
    }
    return variable;
  }

  private boolean convertQualifiers(DfaVariableValue from, DfaVariableValue to) {
    assert from != to;
    if (from.getDependentVariables().isEmpty()) return true;
    List<DfaVariableValue> vars = new ArrayList<>(myVariableStates.keySet());
    for (DfaVariableValue var : vars) {
      DfaVariableValue target = replaceQualifier(var, from, to);
      if (target != var) {
        DfaVariableState fromState = myVariableStates.remove(var);
        if (fromState != null) {
          DfaVariableState toState = myVariableStates.get(target);
          if (toState != null) {
            DfaVariableState resultState = fromState.intersectMap(toState.myFactMap);
            if (resultState == null) return false;
            myVariableStates.put(target, resultState);
          }
          else {
            myVariableStates.put(target, fromState);
          }
        }
      }
    }
    for (int valueId : myIdToEqClassesIndices.keys()) {
      DfaValue value = myFactory.getValue(valueId);
      DfaVariableValue var = ObjectUtils.tryCast(value, DfaVariableValue.class);
      if (var == null || var.getQualifier() != from) continue;
      DfaVariableValue target = var.withQualifier(to);
      if (!uniteClasses(var, target)) return false;
      removeEquivalenceForVariableAndWrappers(var);
    }
    return true;
  }

  private void checkInvariants() {
    if (!LOG.isDebugEnabled() && !ApplicationManager.getApplication().isEAP()) return;
    myIdToEqClassesIndices.forEachEntry((id, classIndex) -> {
      EqClass eqClass = myEqClasses.get(classIndex);
      if (eqClass == null || !eqClass.contains(id)) {
        LOG.error("Invariant violated: null-class for id=" + myFactory.getValue(id));
      }
      return true;
    });
    myDistinctClasses.forEach(DistinctPairSet.DistinctPair::check);
  }

  @Override
  public boolean isNull(DfaValue dfaValue) {
    if (dfaValue instanceof DfaConstValue) return ((DfaConstValue)dfaValue).getValue() == null;

    if (dfaValue instanceof DfaVariableValue) {
      int c1Index = getEqClassIndex(dfaValue);
      return c1Index >= 0 && c1Index == getEqClassIndex(myFactory.getConstFactory().getNull());
    }

    return false;
  }

  @Override
  public boolean isNotNull(DfaValue dfaVar) {
    if (dfaVar instanceof DfaConstValue) return ((DfaConstValue)dfaVar).getValue() != null;
    if (dfaVar instanceof DfaBoxedValue) return true;
    if (dfaVar instanceof DfaFactMapValue) return DfaNullability.isNotNull(((DfaFactMapValue)dfaVar).getFacts());
    if (dfaVar instanceof DfaVariableValue) {
      if (getVariableState((DfaVariableValue)dfaVar).isNotNull()) return true;

      DfaConstValue constantValue = getConstantValue(dfaVar);
      if (constantValue != null && constantValue.getValue() != null) return true;
    }

    DfaConstValue dfaNull = myFactory.getConstFactory().getNull();
    Integer c1Index = getOrCreateEqClassIndex(dfaVar);
    int c2Index = getEqClassIndex(dfaNull);
    if (c1Index == null || c2Index < 0) {
      return false;
    }

    return myDistinctClasses.areDistinctUnordered(c1Index, c2Index);
  }

  @Override
  @Nullable
  @Contract("null -> null")
  public DfaConstValue getConstantValue(@Nullable DfaValue value) {
    if (value instanceof DfaConstValue) {
      return (DfaConstValue)value;
    }
    if (value instanceof DfaUnboxedValue || value instanceof DfaVariableValue) {
      EqClass ec = getEqClass(value);
      return ec == null ? null : (DfaConstValue)unwrap(ec.findConstant(true));
    }
    return null;
  }

  @Override
  public void markEphemeral() {
    myEphemeral = true;
  }

  @Override
  public boolean isEphemeral() {
    return myEphemeral;
  }

  @Override
  public boolean isEmptyStack() {
    return myStack.isEmpty();
  }

  @Override
  public void cleanUpTempVariables() {
    List<DfaVariableValue> values = ContainerUtil.filter(myVariableStates.keySet(), ControlFlowAnalyzer::isTempVariable);
    values.forEach(this::flushVariable);
  }

  @Override
  public boolean castTopOfStack(@NotNull DfaPsiType type) {
    DfaValue value = unwrap(peek());

    DfaFactMap facts = null;
    if (value instanceof DfaVariableValue) {
      DfaVariableValue dfaVar = (DfaVariableValue)value;

      if (isNull(dfaVar)) return true;
      DfaVariableState newState = getVariableState(dfaVar).withInstanceofValue(type);
      if (newState == null) return false;
      setVariableState(dfaVar, newState);
    } else if (value instanceof DfaFactMapValue) {
      facts = ((DfaFactMapValue)value).getFacts();
    }
    if (facts != null) {
      DfaFactMap newFacts = TypeConstraint.withInstanceOf(facts, type);
      if (newFacts == null) return false;
      pop();
      push(myFactory.getFactFactory().createValue(newFacts));
    }
    return true;
  }

  private boolean applyFacts(DfaValue value, DfaFactMap facts) {
    if (value instanceof DfaVariableValue) {
      DfaVariableState oldState = getVariableState((DfaVariableValue)value);
      DfaVariableState newState = oldState.intersectMap(facts);
      if (newState == null) {
        newState = oldState.withoutFact(DfaFactType.TYPE_CONSTRAINT);
        if (newState.intersectMap(facts) != null && DfaNullability.isNotNull(facts)) {
          setVariableState((DfaVariableValue)value, newState);
          return applyRelation(value, getFactory().getConstFactory().getNull(), false);
        }
        return false;
      }
      setVariableState((DfaVariableValue)value, newState);
      if (DfaUtil.isComparedByEquals(newState.getTypeConstraint().getPsiType()) &&
          !newState.getTypeConstraint().equals(oldState.getTypeConstraint())) {
        // Type is narrowed to java.lang.String, java.lang.Integer, etc.: we consider String & boxed types
        // equivalence by content, but other object types by reference, so we need to remove distinct pairs, if any.
        convertReferenceEqualityToValueEquality(value);
      }
      updateEquivalentVariables((DfaVariableValue)value, newState);
      return updateEqClassesByState((DfaVariableValue)value);
    }
    return true;
  }

  private void convertReferenceEqualityToValueEquality(DfaValue value) {
    int id = canonicalize(value).getID();
    Integer index = myIdToEqClassesIndices.get(id);
    assert index != null;
    for (Iterator<DistinctPairSet.DistinctPair> iterator = myDistinctClasses.iterator(); iterator.hasNext(); ) {
      DistinctPairSet.DistinctPair pair = iterator.next();
      EqClass otherClass = pair.getOtherClass(index);
      if (otherClass != null && otherClass.findConstant(false) != getFactory().getConstFactory().getNull()) {
        iterator.remove();
      }
    }
  }

  private boolean updateEqClassesByState(DfaVariableValue value) {
    if (DfaNullability.isNotNull(getVariableState(value).myFactMap)) {
      return applyRelation(value, getFactory().getConstFactory().getNull(), true);
    }
    return true;
  }

  @Override
  public void dropFact(@NotNull DfaValue value, @NotNull DfaFactType<?> factType) {
    if (value instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue)value;
      DfaVariableState state = findVariableState(var);
      if (state != null) {
        state = state.withoutFact(factType);
        setVariableState(var, state);
      }
    }
  }

  @Override
  public <T> boolean applyFact(@NotNull DfaValue value, @NotNull DfaFactType<T> factType, @Nullable T factValue) {
    if (value instanceof DfaFactMapValue) {
      return ((DfaFactMapValue)value).getFacts().intersect(factType, factValue) != null;
    }
    if (value instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue)value;
      if (factValue != null) {
        DfaVariableState state = getVariableState(var);
        DfaVariableState newState = state.intersectFact(factType, factValue);
        if (newState == null) return false;
        setVariableState(var, newState);
        updateEquivalentVariables(var, newState);
        return updateEqClassesByState(var);
      }
    }
    return true;
  }

  static DfaValue unwrap(DfaValue value) {
    if (value instanceof DfaBoxedValue) {
      return ((DfaBoxedValue)value).getWrappedValue();
    }
    if (value instanceof DfaUnboxedValue) {
      return ((DfaUnboxedValue)value).getVariable();
    }
    return value;
  }

  @Override
  public boolean applyContractCondition(DfaValue condition) {
    if (condition instanceof DfaRelationValue) {
      DfaRelationValue relation = (DfaRelationValue)condition;
      if (relation.isEquality() &&
          relation.getRightOperand() == myFactory.getConstFactory().getNull() &&
          (relation.getLeftOperand() instanceof DfaUnknownValue ||
           (relation.getLeftOperand() instanceof DfaVariableValue &&
            getVariableState((DfaVariableValue)relation.getLeftOperand()).getNullability() == Nullability.UNKNOWN))) {
        markEphemeral();
      }
    }
    return applyCondition(condition);
  }

  @Override
  public boolean areEqual(@NotNull DfaValue value1, @NotNull DfaValue value2) {
    if (!(value1 instanceof DfaConstValue) && !(value1 instanceof DfaVariableValue)) return false;
    if (!(value2 instanceof DfaConstValue) && !(value2 instanceof DfaVariableValue)) return false;
    if (value1 == value2) return true;
    int index1 = getEqClassIndex(value1);
    int index2 = getEqClassIndex(value2);
    return index1 != -1 && index1 == index2;
  }

  @Override
  public boolean applyCondition(DfaValue dfaCond) {
    if (dfaCond instanceof DfaUnknownValue) return true;
    if (dfaCond instanceof DfaUnboxedValue) {
      DfaVariableValue dfaVar = ((DfaUnboxedValue)dfaCond).getVariable();
      final DfaValue boxedTrue = myFactory.getBoxedFactory().createBoxed(myFactory.getConstFactory().getTrue());
      return applyRelationCondition(myFactory.getRelationFactory().createRelation(dfaVar, RelationType.EQ, boxedTrue));
    }
    if (dfaCond instanceof DfaVariableValue) {
      DfaVariableValue dfaVar = (DfaVariableValue)dfaCond;
      DfaConstValue dfaTrue = myFactory.getConstFactory().getTrue();
      return applyRelationCondition(myFactory.getRelationFactory().createRelation(dfaVar, RelationType.EQ, dfaTrue));
    }

    if (dfaCond instanceof DfaConstValue) {
      return dfaCond == myFactory.getConstFactory().getTrue() || dfaCond != myFactory.getConstFactory().getFalse();
    }

    if (!(dfaCond instanceof DfaRelationValue)) return true;

    return applyRelationCondition((DfaRelationValue)dfaCond);
  }

  private boolean applyRelationCondition(@NotNull DfaRelationValue dfaRelation) {
    DfaValue dfaLeft = dfaRelation.getLeftOperand();
    DfaValue dfaRight = dfaRelation.getRightOperand();
    RelationType relationType = dfaRelation.getRelation();

    DfaConstValue sentinel = getFactory().getConstFactory().getSentinel();
    if (dfaLeft == sentinel || dfaRight == sentinel) {
      assert relationType == RelationType.EQ || relationType == RelationType.NE;
      return (dfaLeft == dfaRight) == (relationType == RelationType.EQ);
    }
    if (dfaLeft instanceof DfaUnknownValue || dfaRight instanceof DfaUnknownValue) return true;

    LongRangeSet left = getValueFact(dfaLeft, DfaFactType.RANGE);
    LongRangeSet right = getValueFact(dfaRight, DfaFactType.RANGE);

    if (left != null && right != null) {
      if (!applyFact(dfaLeft, DfaFactType.RANGE, right.fromRelation(relationType)) ||
          !applyFact(dfaRight, DfaFactType.RANGE, left.fromRelation(relationType.getFlipped()))) {
        return false;
      }
    }

    if (dfaRight instanceof DfaFactMapValue) {
      DfaFactMapValue factValue = (DfaFactMapValue)dfaRight;
      if ((relationType == RelationType.IS || relationType == RelationType.EQ) &&
          DfaNullability.isNotNull(factValue.getFacts()) &&
          !applyRelation(dfaLeft, getFactory().getConstFactory().getNull(), true)) {
        return false;
      }
      if (dfaLeft instanceof DfaVariableValue) {
        DfaVariableValue dfaVar = (DfaVariableValue)dfaLeft;

        switch (relationType) {
          case IS:
            return applyFacts(dfaVar, factValue.getFacts());
          case IS_NOT: {
            Boolean optionalPresence = factValue.get(DfaFactType.OPTIONAL_PRESENCE);
            if(optionalPresence != null) {
              return applyFact(dfaVar, DfaFactType.OPTIONAL_PRESENCE, !optionalPresence);
            }
            boolean isNotNull = DfaNullability.isNotNull(factValue.getFacts());
            TypeConstraint constraint = factValue.get(DfaFactType.TYPE_CONSTRAINT);
            if (constraint != null && constraint.getNotInstanceofValues().isEmpty()) {
              DfaVariableState state = getVariableState(dfaVar);
              for (DfaPsiType type : constraint.getInstanceofValues()) {
                state = state.withNotInstanceofValue(type);
                if (state == null) {
                  return isNotNull && !getVariableState(dfaVar).isNotNull() &&
                         applyRelation(dfaVar, myFactory.getConstFactory().getNull(), false);
                }
                setVariableState(dfaVar, state);
              }
            }
            return true;
          }
          default:
            return true;
        }
      }
      if (relationType == RelationType.IS || relationType == RelationType.EQ) {
        return getFactMap(dfaLeft).intersect(factValue.getFacts()) != null;
      }
      return true;
    }

    if (isEffectivelyNaN(dfaLeft) || isEffectivelyNaN(dfaRight)) {
      applyEquivalenceRelation(relationType, dfaLeft, dfaRight);
      return relationType == RelationType.NE;
    }
    if ((canBeNaN(dfaLeft) && !isNull(dfaRight)) || (canBeNaN(dfaRight) && !isNull(dfaLeft))) {
      if (dfaLeft == dfaRight && dfaLeft instanceof DfaVariableValue && !(dfaLeft.getType() instanceof PsiPrimitiveType)) {
        return !dfaRelation.isNonEquality();
      }

      applyEquivalenceRelation(relationType, dfaLeft, dfaRight);
      return true;
    }

    return applyEquivalenceRelation(relationType, dfaLeft, dfaRight);
  }

  private void updateVarStateOnComparison(@NotNull DfaVariableValue dfaVar, DfaValue value) {
    if (!(dfaVar.getType() instanceof PsiPrimitiveType)) {
      if (value instanceof DfaConstValue) {
        Object constValue = ((DfaConstValue)value).getValue();
        if (constValue == null) {
          setVariableState(dfaVar, getVariableState(dfaVar).withFact(DfaFactType.NULLABILITY, DfaNullability.NULLABLE));
          return;
        }
        DfaPsiType dfaType = myFactory.createDfaType(((DfaConstValue)value).getType());
        DfaVariableState state = getVariableState(dfaVar).withInstanceofValue(dfaType);
        if (state != null) {
          setVariableState(dfaVar, state);
        }
      }
      if (isNotNull(value) && !isNotNull(dfaVar)) {
        setVariableState(dfaVar, getVariableState(dfaVar).withoutFact(DfaFactType.NULLABILITY));
        applyRelation(dfaVar, myFactory.getConstFactory().getNull(), true);
      }
    }
  }

  private boolean applyEquivalenceRelation(RelationType type, DfaValue dfaLeft, DfaValue dfaRight) {
    boolean isNegated = type == RelationType.NE || type == RelationType.GT || type == RelationType.LT;
    if (!isNegated && type != RelationType.EQ) {
      return true;
    }

    if (dfaLeft == dfaRight) {
      return !isNegated || (dfaLeft instanceof DfaVariableValue && ((DfaVariableValue)dfaLeft).containsCalls());
    }

    if (isNull(dfaLeft) && isNotNull(dfaRight) || isNull(dfaRight) && isNotNull(dfaLeft)) {
      return isNegated;
    }

    if (!isNegated) {
      if (dfaLeft instanceof DfaVariableValue) {
        updateVarStateOnComparison((DfaVariableValue)dfaLeft, dfaRight);
      }
      if (dfaRight instanceof DfaVariableValue) {
        updateVarStateOnComparison((DfaVariableValue)dfaRight, dfaLeft);
      }
    }

    if (type == RelationType.LT) {
      if (!applyLessThanRelation(dfaLeft, dfaRight)) return false;
    } else if (type == RelationType.GT) {
      if (!applyLessThanRelation(dfaRight, dfaLeft)) return false;
    } else {
      if (!isNegated && !applyDependentFieldsEquivalence(dfaLeft, dfaRight)) {
        return false;
      }
      if (!applyRelation(dfaLeft, dfaRight, isNegated)) return false;
    }
    if (!checkCompareWithBooleanLiteral(dfaLeft, dfaRight, isNegated)) {
      return false;
    }
    if (dfaLeft instanceof DfaVariableValue) {
      return applyUnboxedRelation((DfaVariableValue)dfaLeft, dfaRight, isNegated);
    }

    return true;
  }

  @NotNull
  private List<Couple<DfaValue>> getDependentPairs(DfaValue left, DfaValue right) {
    StreamEx<Couple<DfaValue>> stream = StreamEx.empty();
    if (left instanceof DfaVariableValue) {
      stream = stream.append(StreamEx.of(new ArrayList<>(((DfaVariableValue)left).getDependentVariables()))
        .filter(leftVar -> leftVar.getQualifier() == left && leftVar.getSource() instanceof SpecialField)
        .map(leftVar -> Couple.of(leftVar, ((SpecialField)leftVar.getSource()).createValue(myFactory, right))));
    }
    if (right instanceof DfaVariableValue) {
      stream = stream.append(StreamEx.of(new ArrayList<>(((DfaVariableValue)right).getDependentVariables()))
        .filter(rightVar -> rightVar.getQualifier() == right && rightVar.getSource() instanceof SpecialField)
        .map(rightVar -> Couple.of(((SpecialField)rightVar.getSource()).createValue(myFactory, left), rightVar)));
    }
    return stream.distinct().toList();
  }

  private boolean applyDependentFieldsEquivalence(@NotNull DfaValue left, @NotNull DfaValue right) {
    List<Couple<DfaValue>> pairs = getDependentPairs(left, right);
    for (Couple<DfaValue> pair : pairs) {
      if (!applyCondition(myFactory.createCondition(pair.getFirst(), RelationType.EQ, pair.getSecond()))) {
        return false;
      }
    }
    return true;
  }

  private boolean applyUnboxedRelation(@NotNull DfaVariableValue dfaLeft, DfaValue dfaRight, boolean negated) {
    if (negated) {
      // from the fact "wrappers are not the same" it does not follow that "unboxed values are not equal"
      return true;
    }
    PsiType type = dfaLeft.getType();
    if (!TypeConversionUtil.isPrimitiveWrapper(type)) {
      return true;
    }

    DfaBoxedValue.Factory boxedFactory = myFactory.getBoxedFactory();
    DfaValue unboxedLeft = boxedFactory.createUnboxed(dfaLeft);
    DfaValue unboxedRight = boxedFactory.createUnboxed(dfaRight);
    return applyRelation(unboxedLeft, unboxedRight, false) &&
           checkCompareWithBooleanLiteral(unboxedLeft, unboxedRight, false);
  }

  private boolean checkCompareWithBooleanLiteral(DfaValue dfaLeft, DfaValue dfaRight, boolean negated) {
    if (dfaRight instanceof DfaConstValue) {
      Object constVal = ((DfaConstValue)dfaRight).getValue();
      if (constVal instanceof Boolean) {
        boolean boolValue = (Boolean)constVal;
        return applyRelation(dfaLeft, myFactory.getBoolean(!boolValue), !negated) &&
               applyRelation(dfaLeft, myFactory.getBoolean(boolValue), negated);
      }
    }
    return true;
  }

  static boolean isNaN(final DfaValue dfa) {
    return dfa instanceof DfaConstValue && DfaUtil.isNaN(((DfaConstValue)dfa).getValue());
  }

  private boolean applyRelation(@NotNull final DfaValue dfaLeft, @NotNull final DfaValue dfaRight, boolean isNegated) {
    // DfaConstValue || DfaVariableValue
    Integer c1Index = getOrCreateEqClassIndex(dfaLeft);
    Integer c2Index = getOrCreateEqClassIndex(dfaRight);
    if (c1Index == null || c2Index == null) {
      return true;
    }

    ThreeState equalByConstants = equalByConstant(c1Index, c2Index);
    if (equalByConstants != ThreeState.UNSURE) return equalByConstants.toBoolean() != isNegated;
    if (!isNegated) { //Equals
      if (isUnstableValue(dfaLeft) || isUnstableValue(dfaRight)) return true;
      if (!uniteClasses(dfaLeft, dfaRight)) return false;

      for (Iterator<DistinctPairSet.DistinctPair> iterator = myDistinctClasses.iterator(); iterator.hasNext(); ) {
        DistinctPairSet.DistinctPair pair = iterator.next();
        DfaConstValue const1 = (DfaConstValue)pair.getFirst().findConstant(false);
        DfaConstValue const2 = (DfaConstValue)pair.getSecond().findConstant(false);
        if (const1 != null && const2 != null && !preserveConstantDistinction(const1.getValue(), const2.getValue())) {
          iterator.remove();
        }
      }
      myCachedNonTrivialEqClasses = null;
    }
    else { // Not Equals
      if (isNull(dfaLeft) && isPrimitive(dfaRight) || isNull(dfaRight) && isPrimitive(dfaLeft)) return true;
      if (applyBooleanInequality(dfaLeft, dfaRight) ||
          applyBooleanInequality(dfaRight, dfaLeft)) {
        return true;
      }
      myDistinctClasses.addUnordered(c1Index, c2Index);
    }
    myCachedHash = null;

    return true;
  }

  private boolean applyBooleanInequality(DfaValue var, DfaValue value) {
    if (!(var instanceof DfaVariableValue) || !PsiType.BOOLEAN.equals(var.getType())) return false;
    if (!(value instanceof DfaConstValue)) return false;
    Boolean constValue = ObjectUtils.tryCast(((DfaConstValue)value).getValue(), Boolean.class);
    return constValue != null && applyRelation(var, myFactory.getBoolean(!constValue), false);
  }

  private boolean applyLessThanRelation(@NotNull final DfaValue dfaLeft, @NotNull final DfaValue dfaRight) {
    // DfaConstValue || DfaVariableValue
    Integer c1Index = getOrCreateEqClassIndex(dfaLeft);
    Integer c2Index = getOrCreateEqClassIndex(dfaRight);
    if (c1Index == null || c2Index == null) {
      return true;
    }

    ThreeState equalByConstants = equalByConstant(c1Index, c2Index);
    if (equalByConstants != ThreeState.UNSURE) return !equalByConstants.toBoolean();
    if (isNull(dfaLeft) && isPrimitive(dfaRight) || isNull(dfaRight) && isPrimitive(dfaLeft)) return true;
    myCachedHash = null;
    return myDistinctClasses.addOrdered(c1Index, c2Index);
  }

  /**
   * Returns true if value represents an "unstable" value. An unstable value is a value of an object type which could be
   * a newly object every time it's accessed. Such value is still useful as its nullability is stable
   *
   * @param value to check.
   * @return true if value might be unstable, false otherwise
   */
  private boolean isUnstableValue(DfaValue value) {
    if (!(value instanceof DfaVariableValue)) return false;
    DfaVariableValue var = (DfaVariableValue)value;
    PsiModifierListOwner owner = var.getPsiVariable();
    if (!(owner instanceof PsiMethod)) return false;
    if (var.getType() instanceof PsiPrimitiveType) return false;
    if (PropertyUtilBase.isSimplePropertyGetter((PsiMethod)owner)) return false;
    if (isNull(var)) return false;
    return true;
  }

  private static boolean isPrimitive(DfaValue value) {
    return value instanceof DfaVariableValue && value.getType() instanceof PsiPrimitiveType;
  }

  private static boolean preserveConstantDistinction(final Object c1, final Object c2) {
    return c1 == null && c2 instanceof PsiVariable ||
           c2 == null && c1 instanceof PsiVariable;
  }

  @NotNull
  private ThreeState equalByConstant(int i1, int i2) {
    if (i1 == i2) return ThreeState.YES;
    EqClass ec1 = myEqClasses.get(i1);
    EqClass ec2 = myEqClasses.get(i2);
    if (ec1 == null || ec2 == null) return ThreeState.UNSURE;
    DfaValue constOrBox1 = ec1.findConstant(true);
    DfaValue constOrBox2 = ec2.findConstant(true);
    if (constOrBox1 == null || constOrBox2 == null) return ThreeState.UNSURE;
    if (constOrBox1 instanceof DfaConstValue && constOrBox2 instanceof DfaConstValue) {
      return areConstantsEqual((DfaConstValue)constOrBox1, (DfaConstValue)constOrBox2);
    }
    if (constOrBox1 instanceof DfaBoxedValue && constOrBox2 instanceof DfaBoxedValue) {
      DfaValue wrapped1 = ((DfaBoxedValue)constOrBox1).getWrappedValue();
      DfaValue wrapped2 = ((DfaBoxedValue)constOrBox2).getWrappedValue();
      if (wrapped1 instanceof DfaConstValue && wrapped2 instanceof DfaConstValue &&
          areConstantsEqual((DfaConstValue)wrapped1, (DfaConstValue)wrapped2) == ThreeState.NO) {
        return ThreeState.NO;
      }
    }
    return ThreeState.UNSURE;
  }

  private static ThreeState areConstantsEqual(DfaConstValue const1, DfaConstValue const2) {
    Number value1 = ObjectUtils.tryCast(const1.getValue(), Number.class);
    Number value2 = ObjectUtils.tryCast(const2.getValue(), Number.class);
    if (value1 == null || value2 == null) return ThreeState.UNSURE;
    if (value1 instanceof Long && value2 instanceof Long) return ThreeState.fromBoolean(value1.equals(value2));
    return ThreeState.fromBoolean(value1.doubleValue() == value2.doubleValue());
  }

  @Override
  public boolean checkNotNullable(DfaValue value) {
    if (value == myFactory.getConstFactory().getNull()) return false;
    if (value instanceof DfaFactMapValue && DfaNullability.isNullable(((DfaFactMapValue)value).getFacts())) return false;

    if (value instanceof DfaVariableValue) {
      DfaVariableValue varValue = (DfaVariableValue)value;
      if (varValue.getType() instanceof PsiPrimitiveType) return true;
      if (isNotNull(varValue)) return true;
      return getVariableState(varValue).getNullability() != Nullability.NULLABLE;
    }
    return true;
  }

  @Override
  @Nullable
  @SuppressWarnings("unchecked")
  public <T> T getValueFact(@NotNull DfaValue value, @NotNull DfaFactType<T> factType) {
    if (value instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue)value;
      DfaVariableState state = findVariableState(var);
      if (state != null) {
        T fact = state.getFact(factType);
        if (fact != null) {
          return fact;
        }
      }
      value = resolveVariableValue(var);
    }
    return factType.fromDfaValue(value);
  }

  @Override
  public <T> void forceVariableFact(@NotNull DfaVariableValue var, @NotNull DfaFactType<T> factType, @Nullable T value) {
    DfaVariableState state = getVariableState(var);
    removeEquivalenceForVariableAndWrappers(var);
    setVariableState(var, state.withFact(factType, value));
    updateEqClassesByState(var);
  }

  @NotNull
  private DfaValue resolveVariableValue(DfaVariableValue var) {
    DfaConstValue constValue = getConstantValue(var);
    if (constValue != null) {
      return constValue;
    }
    return var;
  }

  DfaFactMap getFactMap(@NotNull DfaValue value) {
    if (value instanceof DfaVariableValue) {
      DfaVariableState state = findVariableState((DfaVariableValue)value);
      if (state != null) {
        return state.myFactMap;
      }
      value = resolveVariableValue((DfaVariableValue)value);
    }
    return DfaFactMap.fromDfaValue(value);
  }

  void setVariableState(DfaVariableValue dfaVar, DfaVariableState state) {
    dfaVar = canonicalize(dfaVar);
    if (state.equals(myDefaultVariableStates.get(dfaVar))) {
      myVariableStates.remove(dfaVar);
    } else {
      myVariableStates.put(dfaVar, state);
    }
    myCachedHash = null;
  }

  protected void updateEquivalentVariables(DfaVariableValue dfaVar, DfaVariableState state) {
    EqClass eqClass = getEqClass(dfaVar);
    if (eqClass != null) {
      for (DfaValue value : eqClass.getMemberValues()) {
        if (value != dfaVar && value instanceof DfaVariableValue) {
          setVariableState((DfaVariableValue)value, state);
        }
      }
    }
  }

  @NotNull
  private DfaValue canonicalize(@NotNull DfaValue value) {
    if (value instanceof DfaVariableValue) {
      return canonicalize((DfaVariableValue)value);
    }
    if (value instanceof DfaBoxedValue && ((DfaBoxedValue)value).getWrappedValue() instanceof DfaVariableValue) {
      return Objects.requireNonNull(myFactory.getBoxedFactory().createBoxed(canonicalize(((DfaBoxedValue)value).getWrappedValue())));
    }
    if (value instanceof DfaUnboxedValue) {
      return myFactory.getBoxedFactory().createUnboxed(canonicalize(((DfaUnboxedValue)value).getVariable()));
    }
    if (value instanceof DfaConstValue) {
      Object constant = ((DfaConstValue)value).getValue();
      if (Double.valueOf(-0.0).equals(constant)) {
        return myFactory.getConstFactory().createFromValue(0.0, PsiType.DOUBLE, null);
      }
    }
    return value;
  }

  @NotNull
  private DfaVariableValue canonicalize(DfaVariableValue var) {
    DfaVariableValue qualifier = var.getQualifier();
    if (qualifier != null) {
      EqClass eqClass = getEqClass(qualifier);
      return var.withQualifier(eqClass == null ? canonicalize(qualifier) : Objects.requireNonNull(eqClass.getCanonicalVariable()));
    }
    return var;
  }

  private DfaVariableState findVariableState(DfaVariableValue var) {
    DfaVariableState state = myVariableStates.get(var);
    if (state != null) {
      return state;
    }
    DfaVariableValue canonicalized = canonicalize(var);
    return canonicalized == var ? null : myVariableStates.get(canonicalized);
  }

  @NotNull
  DfaVariableState getVariableState(DfaVariableValue dfaVar) {
    DfaVariableState state = findVariableState(dfaVar);

    if (state == null) {
      state = myDefaultVariableStates.get(dfaVar);
      if (state == null) {
        state = createVariableState(dfaVar);
        DfaPsiType initialType = dfaVar.getDfaType();
        if (initialType != null) {
          state = state.withInstanceofValue(initialType);
          assert state != null;
        }
        myDefaultVariableStates.put(dfaVar, state);
      }
    }

    return state;
  }

  void forVariableStates(BiConsumer<? super DfaVariableValue, ? super DfaVariableState> consumer) {
    myVariableStates.forEach(consumer);
  }

  @NotNull
  protected DfaVariableState createVariableState(@NotNull DfaVariableValue var) {
    return new DfaVariableState(var);
  }

  @Override
  public void flushFields() {
    Set<DfaVariableValue> vars = ContainerUtil.newLinkedHashSet(getChangedVariables());
    for (EqClass aClass : myEqClasses) {
      if (aClass != null) {
        vars.addAll(aClass.getVariables(true));
      }
    }
    for (DfaVariableValue value : vars) {
      if (!value.isFlushableByCalls()) continue;
      DfaVariableValue qualifier = value.getQualifier();
      if (qualifier != null) {
        if (getValueFact(qualifier, DfaFactType.MUTABILITY) == Mutability.UNMODIFIABLE ||
            Boolean.TRUE.equals(getValueFact(qualifier, DfaFactType.LOCALITY))) {
          continue;
        }
      }
      doFlush(value, shouldMarkFlushed(value));
    }
  }

  private boolean shouldMarkFlushed(@NotNull DfaVariableValue value) {
    if (value.getInherentNullability() != Nullability.NULLABLE) return false;
    return getVariableState(value).getFact(DfaFactType.NULLABILITY) == DfaNullability.FLUSHED || isNull(value) || isNotNull(value);
  }

  @NotNull
  Set<DfaVariableValue> getChangedVariables() {
    return myVariableStates.keySet();
  }

  @Override
  public void flushVariable(@NotNull final DfaVariableValue variable) {
    EqClass eqClass = variable.getDependentVariables().isEmpty() ? null : getEqClass(variable);
    DfaVariableValue newCanonical =
      eqClass == null ? null : StreamEx.of(eqClass.getVariables(false)).without(variable).min(EqClass.CANONICAL_VARIABLE_COMPARATOR)
        .orElse(null);
    myStack.replaceAll(value -> handleStackValueOnVariableFlush(value, variable, newCanonical));

    doFlush(variable, false);
    flushDependencies(variable);
    myCachedHash = null;
  }

  void flushDependencies(@NotNull DfaVariableValue variable) {
    for (DfaVariableValue dependent : variable.getDependentVariables()) {
      doFlush(dependent, false);
    }
  }

  private void flushQualifiedMethods(@NotNull DfaVariableValue variable) {
    PsiModifierListOwner psiVariable = variable.getPsiVariable();
    DfaVariableValue qualifier = variable.getQualifier();
    if (psiVariable instanceof PsiField && qualifier != null) {
      // Flush method results on field write
      List<DfaVariableValue> toFlush =
        qualifier.getDependentVariables().stream().filter(DfaVariableValue::containsCalls).collect(Collectors.toList());
      toFlush.forEach(val -> doFlush(val, shouldMarkFlushed(val)));
    }
  }

  void doFlush(@NotNull DfaVariableValue var, boolean markFlushed) {
    if(isNull(var)) {
      myStack.replaceAll(val -> val == var ? myFactory.getConstFactory().getNull() : val);
    }

    removeEquivalenceForVariableAndWrappers(var);
    myVariableStates.remove(var);
    if (markFlushed) {
      setVariableState(var, getVariableState(var).withFact(DfaFactType.NULLABILITY, DfaNullability.FLUSHED));
    }
    myCachedHash = null;
  }

  private void removeEquivalence(DfaValue var) {
    int varID = var.getID();
    Integer varClassIndex = myIdToEqClassesIndices.get(varID);
    if (varClassIndex == null) return;

    EqClass varClass = myEqClasses.get(varClassIndex);

    varClass = new EqClass(varClass);
    DfaVariableValue previousCanonical = varClass.getCanonicalVariable();
    myEqClasses.set(varClassIndex, varClass);
    varClass.removeValue(varID);
    myIdToEqClassesIndices.remove(varID);
    checkInvariants();

    if (varClass.isEmpty()) {
      myEqClasses.set(varClassIndex, null);

      for (Iterator<DistinctPairSet.DistinctPair> iterator = myDistinctClasses.iterator(); iterator.hasNext(); ) {
        DistinctPairSet.DistinctPair pair = iterator.next();
        if (pair.getOtherClass(varClassIndex) != null) {
          iterator.remove();
        }
      }
    }
    else if (varClass.containsConstantsOnly()) {
      for (Iterator<DistinctPairSet.DistinctPair> iterator = myDistinctClasses.iterator(); iterator.hasNext(); ) {
        DistinctPairSet.DistinctPair pair = iterator.next();
        EqClass other = pair.getOtherClass(varClassIndex);
        if (other != null && other.containsConstantsOnly()) {
          iterator.remove();
        }
      }
    }
    else {
      DfaVariableValue newCanonical = varClass.getCanonicalVariable();
      if (newCanonical != null && previousCanonical != null &&
          previousCanonical != newCanonical && newCanonical.getDepth() <= previousCanonical.getDepth()) {
        // Do not transfer to deeper qualifier. E.g. if we have two classes like (a, b.c) (a.d, e),
        // and flushing `a`, we do not convert `a.d` to `b.c.d`. Otherwise infinite qualifier explosion is possible.
        boolean successfullyConverted = convertQualifiers(previousCanonical, newCanonical);
        assert successfullyConverted;
      }
    }

    myCachedNonTrivialEqClasses = null;
    myCachedHash = null;
  }

  void removeEquivalenceForVariableAndWrappers(@NotNull DfaVariableValue var) {
    removeEquivalence(var);
    DfaValue wrapped = myFactory.getBoxedFactory().getWrappedIfExists(var);
    if (wrapped != null) {
      removeEquivalence(wrapped);
    }
  }

  private class MyIdMap extends TIntObjectHashMap<Integer> {
    @Override
    public String toString() {
      final StringBuilder s = new StringBuilder("{");
      forEachEntry(new TIntObjectProcedure<Integer>() {
        @Override
        public boolean execute(int id, Integer index) {
          DfaValue value = myFactory.getValue(id);
          s.append(value).append(" -> ").append(index).append(", ");
          return true;
        }
      });
      s.append("}");
      return s.toString();
    }
  }
}
