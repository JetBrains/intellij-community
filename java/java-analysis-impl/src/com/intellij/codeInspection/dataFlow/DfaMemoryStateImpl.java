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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;


public class DfaMemoryStateImpl implements DfaMemoryState {
  private static final Logger LOG = Logger.getInstance(DfaMemoryStateImpl.class);

  private final DfaValueFactory myFactory;

  private final List<EqClass> myEqClasses;
  // dfa value id -> indices in myEqClasses list of the classes which contain the id (or wrapped)
  private final TIntObjectHashMap<int[]> myIdToEqClassesIndices;
  private final Stack<DfaValue> myStack;
  private final DistinctPairSet myDistinctClasses;
  private final LinkedHashMap<DfaVariableValue,DfaVariableState> myVariableStates;
  private final Map<DfaVariableValue,DfaVariableState> myDefaultVariableStates; 
  private final LinkedHashSet<DfaVariableValue> myUnknownVariables;
  private boolean myEphemeral;

  protected DfaMemoryStateImpl(final DfaValueFactory factory) {
    myFactory = factory;
    myDefaultVariableStates = ContainerUtil.newTroveMap();
    myEqClasses = ContainerUtil.newArrayList();
    myUnknownVariables = ContainerUtil.newLinkedHashSet();
    myVariableStates = ContainerUtil.newLinkedHashMap();
    myDistinctClasses = new DistinctPairSet(this);
    myStack = new Stack<>();
    myIdToEqClassesIndices = new MyIdMap(20);
  }

  protected DfaMemoryStateImpl(DfaMemoryStateImpl toCopy) {
    myFactory = toCopy.myFactory;
    myEphemeral = toCopy.myEphemeral;
    myDefaultVariableStates = toCopy.myDefaultVariableStates; // shared between all states
    
    myStack = new Stack<>(toCopy.myStack);
    myDistinctClasses = new DistinctPairSet(this, toCopy.myDistinctClasses);
    myUnknownVariables = ContainerUtil.newLinkedHashSet(toCopy.myUnknownVariables);

    myEqClasses = ContainerUtil.newArrayList(toCopy.myEqClasses);
    myIdToEqClassesIndices = new MyIdMap(toCopy.myIdToEqClassesIndices.size());
    toCopy.myIdToEqClassesIndices.forEachEntry((id, set) -> {
      myIdToEqClassesIndices.put(id, set);
      return true;
    });
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
    return equalsSuperficially(that) && equalsByUnknownVariables(that) && equalsByRelations(that) && equalsByVariableStates(that);
  }

  private boolean equalsByUnknownVariables(DfaMemoryStateImpl that) {
    return myUnknownVariables.equals(that.myUnknownVariables);
  }

  Object getSuperficialKey() {
    return Pair.create(myEphemeral, myStack);
  }

  private boolean equalsSuperficially(DfaMemoryStateImpl other) {
    return myEphemeral == other.myEphemeral && myStack.equals(other.myStack);
  }

  boolean equalsByRelations(DfaMemoryStateImpl that) {
    return getNonTrivialEqClasses().equals(that.getNonTrivialEqClasses()) && getDistinctClassPairs().equals(that.getDistinctClassPairs());
  }

  boolean equalsByVariableStates(DfaMemoryStateImpl that) {
    return myVariableStates.equals(that.myVariableStates);
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

    int hash = getPartialHashCode(true, true);
    myCachedHash = hash;
    return hash;
  }

  int getPartialHashCode(boolean unknowns, boolean varStates) {
    int hash = (getNonTrivialEqClasses().hashCode() * 31 +
              getDistinctClassPairs().hashCode()) * 31 +
             myStack.hashCode();
    if (varStates) {
      hash = hash * 31 + myVariableStates.hashCode();
    }
    if (unknowns && !myUnknownVariables.isEmpty()) {
      hash = hash * 31 + myUnknownVariables.hashCode();
    }
    return hash;
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
    if (!myUnknownVariables.isEmpty()) {
      result.append("\n  unknowns: ").append(new HashSet<>(myUnknownVariables));
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

    value = handleFlush(var, value);
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
      setVariableState(var, isNull(value) ? state.withFact(DfaFactType.CAN_BE_NULL, true) : state);
      DfaRelationValue dfaEqual = myFactory.getRelationFactory().createRelation(var, RelationType.EQ, value);
      if (dfaEqual == null) return;
      applyCondition(dfaEqual);

      if (value instanceof DfaVariableValue) {
        setVariableState(var, getVariableState((DfaVariableValue)value));
      }
    }

    updateEqClassesByState(var);
  }

  private DfaValue handleFlush(DfaVariableValue flushed, DfaValue value) {
    if (value instanceof DfaVariableValue && (value == flushed || flushed.getDependentVariables().contains(value))) {
      Nullability nullability = isNotNull(value) ? Nullability.NOT_NULL :
                                isUnknownState(value) ? Nullability.UNKNOWN : ((DfaVariableValue)value).getInherentNullability();
      return myFactory.createTypeValue(((DfaVariableValue)value).getVariableType(), nullability);
    }
    return value;
  }

  @Nullable("for boxed values which can't be compared by ==")
  private Integer getOrCreateEqClassIndex(@NotNull DfaValue dfaValue) {
    int i = getEqClassIndex(dfaValue);
    if (i != -1) return i;
    if (!canBeInRelation(dfaValue) ||
        !canBeReused(dfaValue) && !(((DfaBoxedValue)dfaValue).getWrappedValue() instanceof DfaConstValue)) {
      return null;
    }
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
    addToMap(dfaValue.getID(), resultIndex);
    checkInvariants();

    return tryMergeClassByQualifier(resultIndex);
  }

  /**
   * Given a class index which contains single value, tries to find equivalent class
   * based on qualifier equivalence. E.g. if {@code classIndex} is {@code [length|s1]}
   * and there are another classes {@code [s1, s2]} and {@code [length|s2]}, then
   * {@code [length|s1, length|s2]} created and returned (if strings s1 and s2 are the same,
   * then their lengths are also the same).
   *
   * @param classIndex index of a class to merge (should contain single element)
   * @return an index of a merged class or original classIndex if merging is impossible.
   */
  private int tryMergeClassByQualifier(int classIndex) {
    List<DfaValue> values = myEqClasses.get(classIndex).getMemberValues();
    if (values.size() != 1) return classIndex;
    DfaValue dfaValue = values.get(0);
    if (!(dfaValue instanceof DfaVariableValue)) return classIndex;
    DfaVariableValue variableValue = (DfaVariableValue)dfaValue;
    DfaVariableValue qualifier = variableValue.getQualifier();
    if (qualifier == null) return classIndex;
    Integer index = getOrCreateEqClassIndex(qualifier);
    if (index == null) return classIndex;
    for (DfaValue eqQualifier : myEqClasses.get(index).getMemberValues()) {
      if (eqQualifier != qualifier && eqQualifier instanceof DfaVariableValue) {
        DfaVariableValue eqValue = variableValue.withQualifier((DfaVariableValue)eqQualifier);
        int i = getEqClassIndex(eqValue);
        if (i != -1) {
          uniteClasses(i, classIndex);
          return i;
        }
      }
    }
    return classIndex;
  }

  private void addToMap(int id, int index) {
    id = unwrap(myFactory.getValue(id)).getID();
    int[] classes = myIdToEqClassesIndices.get(id);
    if (classes == null) {
      classes = new int[]{index};
    }
    else {
      classes = ArrayUtil.append(classes, index);
    }
    myIdToEqClassesIndices.put(id, classes);
  }

  private void removeFromMap(int id, int index) {
    id = unwrap(myFactory.getValue(id)).getID();
    int[] classes = myIdToEqClassesIndices.get(id);
    if (classes != null) {
      int i = ArrayUtil.indexOf(classes, index);
      if (i != -1) {
        classes = ArrayUtil.remove(classes, i);
        myIdToEqClassesIndices.put(id, classes);
      }
    }
  }

  private void removeAllFromMap(int id) {
    if (id < 0) return;
    id = unwrap(myFactory.getValue(id)).getID();
    myIdToEqClassesIndices.remove(id);
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
    if (!equalsByUnknownVariables(that) ||
        !that.getDistinctClassPairs().containsAll(getDistinctClassPairs())) {
      return false;
    }
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
    int index = getEqClassIndex(dfaValue);
    EqClass set = index == -1 ? null : myEqClasses.get(index);
    if (set == null) {
      return Collections.emptyList();
    }
    return set.getMemberValues();
  }

  private boolean canBeNaN(@NotNull DfaValue dfaValue) {
    for (DfaValue eq : getEquivalentValues(dfaValue)) {
      if (eq instanceof DfaBoxedValue) {
        eq = ((DfaBoxedValue)eq).getWrappedValue();
      }
      if (eq instanceof DfaConstValue && !isNaN(eq)) {
        return false;
      }
    }

    return dfaValue instanceof DfaVariableValue && TypeConversionUtil.isFloatOrDoubleType(((DfaVariableValue)dfaValue).getVariableType());
  }


  private boolean isEffectivelyNaN(@NotNull DfaValue dfaValue) {
    for (DfaValue eqClass : getEquivalentValues(dfaValue)) {
      if (isNaN(eqClass)) return true;
    }
    return false;
  }

  List<EqClass> getEqClasses() {
    return myEqClasses;
  }

  int getEqClassIndex(@NotNull final DfaValue dfaValue) {
    final int id = unwrap(dfaValue).getID();
    int[] classes = myIdToEqClassesIndices.get(id);

    int result = -1;
    if (classes != null) {
      for (int index : classes) {
        EqClass aClass = myEqClasses.get(index);
        if (!aClass.contains(dfaValue.getID())) continue;
        if (!canBeReused(dfaValue) && aClass.size() > 1) break;
        result = index;
        break;
      }
    }
    return result;
  }

  private boolean canBeReused(@NotNull DfaValue dfaValue) {
    if (dfaValue instanceof DfaBoxedValue) {
      DfaValue valueToWrap = ((DfaBoxedValue)dfaValue).getWrappedValue();
      if (valueToWrap instanceof DfaConstValue) {
        return cacheable((DfaConstValue)valueToWrap);
      }
      if (valueToWrap instanceof DfaVariableValue) {
        if (PsiType.BOOLEAN.equals(((DfaVariableValue)valueToWrap).getVariableType())) return true;
        for (DfaValue value : getEquivalentValues(valueToWrap)) {
          if (value instanceof DfaConstValue && cacheable((DfaConstValue)value)) return true;
        }
      }
      return false;
    }
    return true;
  }

  private static boolean cacheable(@NotNull DfaConstValue dfaConstValue) {
    Object value = dfaConstValue.getValue();
    return box(value) == box(value);
  }

  @SuppressWarnings({"UnnecessaryBoxing", "UnnecessaryUnboxing"})
  private static Object box(final Object value) {
    Object newBoxedValue;
    if (value instanceof Integer) {
      newBoxedValue = Integer.valueOf(((Integer)value).intValue());
    }
    else if (value instanceof Byte) {
      newBoxedValue = Byte.valueOf(((Byte)value).byteValue());
    }
    else if (value instanceof Short) {
      newBoxedValue = Short.valueOf(((Short)value).shortValue());
    }
    else if (value instanceof Long) {
      newBoxedValue = Long.valueOf(((Long)value).longValue());
    }
    else if (value instanceof Boolean) {
      newBoxedValue = Boolean.valueOf(((Boolean)value).booleanValue());
    }
    else if (value instanceof Character) {
      newBoxedValue = Character.valueOf(((Character)value).charValue());
    }
    else {
      return new Object();
    }
    return newBoxedValue;
  }

  private boolean uniteClasses(int c1Index, int c2Index) {
    if (!myDistinctClasses.unite(c1Index, c2Index)) return false;

    EqClass c1 = myEqClasses.get(c1Index);
    EqClass c2 = myEqClasses.get(c2Index);

    Set<DfaVariableValue> vars = ContainerUtil.newTroveSet();
    Set<DfaVariableValue> negatedVars = ContainerUtil.newTroveSet();
    int[] cs = new int[c1.size() + c2.size()];
    c1.set(0, cs, 0, c1.size());
    c2.set(0, cs, c1.size(), c2.size());

    int nConst = 0;
    for (int c : cs) {
      DfaValue dfaValue = unwrap(myFactory.getValue(c));
      if (dfaValue instanceof DfaConstValue) nConst++;
      if (dfaValue instanceof DfaVariableValue) {
        DfaVariableValue variableValue = (DfaVariableValue)dfaValue;
        if (variableValue.isNegated()) {
          negatedVars.add(variableValue.createNegated());
        }
        else {
          vars.add(variableValue);
        }
      }
      if (nConst > 1) return false;
    }
    if (ContainerUtil.intersects(vars, negatedVars)) return false;

    EqClass newClass = new EqClass(c1);

    myEqClasses.set(c1Index, newClass);
    for (int i = 0; i < c2.size(); i++) {
      int c = c2.get(i);
      newClass.add(c);
      removeFromMap(c, c2Index);
      addToMap(c, c1Index);
    }

    myEqClasses.set(c2Index, null);
    checkInvariants();

    return true;
  }

  private void checkInvariants() {
    if (!LOG.isDebugEnabled() && !ApplicationManager.getApplication().isEAP()) return;
    myIdToEqClassesIndices.forEachEntry((id, eqClasses) -> {
      for (int classNum : eqClasses) {
        if (myEqClasses.get(classNum) == null) {
          LOG.error("Invariant violated: null-class for id=" + myFactory.getValue(id));
        }
      }
      return true;
    });
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
    if (dfaVar instanceof DfaFactMapValue) return Boolean.FALSE.equals(((DfaFactMapValue)dfaVar).get(DfaFactType.CAN_BE_NULL));
    if (dfaVar instanceof DfaVariableValue) {
      if (getVariableState((DfaVariableValue)dfaVar).isNotNull()) return true;

      DfaConstValue constantValue = getConstantValue((DfaVariableValue)dfaVar);
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
  public DfaConstValue getConstantValue(@NotNull DfaVariableValue value) {
    int index = getEqClassIndex(value);
    EqClass ec = index == -1 ? null : myEqClasses.get(index);
    return ec == null ? null : (DfaConstValue)unwrap(ec.findConstant(true));
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
    Predicate<DfaVariableValue> sharesState = var ->
      getConstantValue(var) == null &&
      StreamEx.of(getEquivalentValues(var)).without(var).select(DfaVariableValue.class).findFirst().isPresent();
    List<DfaVariableValue> values = StreamEx.ofKeys(myVariableStates)
      .filter(ControlFlowAnalyzer::isTempVariable)
      .remove(sharesState)
      .toList();
    values.forEach(this::flushVariable);
  }

  @Override
  public boolean castTopOfStack(@NotNull DfaPsiType type) {
    DfaValue value = unwrap(peek());

    DfaFactMap facts = null;
    if (value instanceof DfaVariableValue) {
      DfaVariableValue dfaVar = (DfaVariableValue)value;

      if (isNull(dfaVar)) return true;
      if (isUnknownState(dfaVar)) {
        facts = getVariableState(dfaVar).myFactMap;
      } else {
        DfaVariableState newState = getVariableState(dfaVar).withInstanceofValue(type);
        if (newState == null) return false;
        setVariableState(dfaVar, newState);
      }
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
    if (value instanceof DfaVariableValue && !isUnknownState(value)) {
      DfaVariableState oldState = getVariableState((DfaVariableValue)value);
      DfaVariableState newState = oldState.intersectMap(facts);
      if (newState == null) {
        newState = oldState.withoutFact(DfaFactType.TYPE_CONSTRAINT);
        if (newState.intersectMap(facts) != null && !Boolean.FALSE.equals(facts.get(DfaFactType.CAN_BE_NULL))) {
          setVariableState((DfaVariableValue)value, newState);
          return applyRelation(value, getFactory().getConstFactory().getNull(), false);
        }
        return false;
      }
      setVariableState((DfaVariableValue)value, newState);
      updateEquivalentVariables((DfaVariableValue)value, newState);
      return updateEqClassesByState((DfaVariableValue)value);
    }
    return true;
  }

  private boolean updateEqClassesByState(DfaVariableValue value) {
    if (Boolean.FALSE.equals(getVariableState(value).getFact(DfaFactType.CAN_BE_NULL))) {
      return applyRelation(value, getFactory().getConstFactory().getNull(), true);
    }
    return true;
  }

  @Override
  public void dropFact(@NotNull DfaValue value, @NotNull DfaFactType<?> factType) {
    if (value instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue)value;
      if (!isUnknownState(var)) {
        DfaVariableState state = findVariableState(var);
        if (state != null) {
          state = state.withoutFact(factType);
          setVariableState(var, state);
        }
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
      if (!isUnknownState(var) && factValue != null) {
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
  public boolean applyCondition(DfaValue dfaCond) {
    if (dfaCond instanceof DfaUnknownValue) return true;
    if (dfaCond instanceof DfaUnboxedValue) {
      DfaVariableValue dfaVar = ((DfaUnboxedValue)dfaCond).getVariable();
      boolean isNegated = dfaVar.isNegated();
      DfaVariableValue dfaNormalVar = isNegated ? dfaVar.createNegated() : dfaVar;
      final DfaValue boxedTrue = myFactory.getBoxedFactory().createBoxed(myFactory.getConstFactory().getTrue());
      return applyRelationCondition(
        myFactory.getRelationFactory().createRelation(dfaNormalVar, RelationType.equivalence(!isNegated), boxedTrue));
    }
    if (dfaCond instanceof DfaVariableValue) {
      DfaVariableValue dfaVar = (DfaVariableValue)dfaCond;
      boolean isNegated = dfaVar.isNegated();
      DfaVariableValue dfaNormalVar = isNegated ? dfaVar.createNegated() : dfaVar;
      DfaConstValue dfaTrue = myFactory.getConstFactory().getTrue();
      return applyRelationCondition(
        myFactory.getRelationFactory().createRelation(dfaNormalVar, RelationType.equivalence(!isNegated), dfaTrue));
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
          Boolean.FALSE.equals(factValue.get(DfaFactType.CAN_BE_NULL)) &&
          !applyRelation(dfaLeft, getFactory().getConstFactory().getNull(), true)) {
        return false;
      }
      if (dfaLeft instanceof DfaVariableValue) {
        DfaVariableValue dfaVar = (DfaVariableValue)dfaLeft;
        if (isUnknownState(dfaVar)) {
          if (relationType == RelationType.IS_NOT) {
            DfaPsiType dfaType = dfaVar.getDfaType();
            TypeConstraint constraint = factValue.get(DfaFactType.TYPE_CONSTRAINT);
            if (dfaType != null && constraint != null) {
              return constraint.getInstanceofValues().stream().noneMatch(type -> type.isAssignableFrom(dfaType));
            }
          }
          return true;
        }

        switch (relationType) {
          case IS:
            return applyFacts(dfaVar, factValue.getFacts());
          case IS_NOT: {
            Boolean optionalPresence = factValue.get(DfaFactType.OPTIONAL_PRESENCE);
            if(optionalPresence != null) {
              return applyFact(dfaVar, DfaFactType.OPTIONAL_PRESENCE, !optionalPresence);
            }
            Boolean canBeNull = factValue.get(DfaFactType.CAN_BE_NULL);
            TypeConstraint constraint = factValue.get(DfaFactType.TYPE_CONSTRAINT);
            if (constraint != null && constraint.getNotInstanceofValues().isEmpty()) {
              DfaVariableState state = getVariableState(dfaVar);
              for (DfaPsiType type : constraint.getInstanceofValues()) {
                state = state.withNotInstanceofValue(type);
                if (state == null) {
                  return Boolean.FALSE.equals(canBeNull) &&
                         !getVariableState(dfaVar).isNotNull() &&
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
      applyEquivalenceRelation(dfaRelation, dfaLeft, dfaRight);
      return relationType == RelationType.NE;
    }
    if ((canBeNaN(dfaLeft) && !isNull(dfaRight)) || (canBeNaN(dfaRight) && !isNull(dfaLeft))) {
      if (dfaLeft == dfaRight &&
          dfaLeft instanceof DfaVariableValue &&
          !(((DfaVariableValue)dfaLeft).getVariableType() instanceof PsiPrimitiveType)) {
        return !dfaRelation.isNonEquality();
      }

      applyEquivalenceRelation(dfaRelation, dfaLeft, dfaRight);
      return true;
    }

    return applyEquivalenceRelation(dfaRelation, dfaLeft, dfaRight);
  }

  private void updateVarStateOnComparison(@NotNull DfaVariableValue dfaVar, DfaValue value) {
    if (!isUnknownState(dfaVar) && !(dfaVar.getVariableType() instanceof PsiPrimitiveType)) {
      if (value instanceof DfaConstValue) {
        Object constValue = ((DfaConstValue)value).getValue();
        if (constValue == null) {
          setVariableState(dfaVar, getVariableState(dfaVar).withFact(DfaFactType.CAN_BE_NULL, true));
          return;
        }
        DfaPsiType dfaType = myFactory.createDfaType(((DfaConstValue)value).getType());
        DfaVariableState state = getVariableState(dfaVar).withInstanceofValue(dfaType);
        if (state != null) {
          setVariableState(dfaVar, state);
        }
      }
      if (isNotNull(value) && !isNotNull(dfaVar)) {
        setVariableState(dfaVar, getVariableState(dfaVar).withoutFact(DfaFactType.CAN_BE_NULL));
        applyRelation(dfaVar, myFactory.getConstFactory().getNull(), true);
      }
    }
  }

  private boolean applyEquivalenceRelation(@NotNull DfaRelationValue dfaRelation, DfaValue dfaLeft, DfaValue dfaRight) {
    boolean isNegated = dfaRelation.isNonEquality();
    if (!isNegated && !dfaRelation.isEquality()) {
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

    if (dfaRelation.getRelation() == RelationType.LT) {
      if (!applyLessThanRelation(dfaLeft, dfaRight)) return false;
    } else if (dfaRelation.getRelation() == RelationType.GT) {
      if (!applyLessThanRelation(dfaRight, dfaLeft)) return false;
    } else {
      if (!applyRelation(dfaLeft, dfaRight, isNegated)) return false;
    }
    if (!checkCompareWithBooleanLiteral(dfaLeft, dfaRight, isNegated)) {
      return false;
    }
    if (dfaLeft instanceof DfaVariableValue) {
      return applyUnboxedRelation((DfaVariableValue)dfaLeft, dfaRight, isNegated) &&
             applyBoxedRelation((DfaVariableValue)dfaLeft, dfaRight, isNegated);
    }

    return true;
  }

  private boolean applyBoxedRelation(@NotNull DfaVariableValue dfaLeft, DfaValue dfaRight, boolean negated) {
    if (!TypeConversionUtil.isPrimitiveAndNotNull(dfaLeft.getVariableType())) return true;

    DfaBoxedValue.Factory boxedFactory = myFactory.getBoxedFactory();
    DfaValue boxedLeft = boxedFactory.createBoxed(dfaLeft);
    DfaValue boxedRight = boxedFactory.createBoxed(dfaRight);
    return boxedLeft == null || boxedRight == null || applyRelation(boxedLeft, boxedRight, negated);
  }

  private boolean applyUnboxedRelation(@NotNull DfaVariableValue dfaLeft, DfaValue dfaRight, boolean negated) {
    if (negated) {
      // from the fact "wrappers are not the same" it does not follow that "unboxed values are not equal"
      return true;
    }
    PsiType type = dfaLeft.getVariableType();
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
        DfaConstValue negVal = myFactory.getBoolean(!(Boolean)constVal);
        return applyRelation(dfaLeft, negVal, !negated) &&
               applyRelation(dfaLeft.createNegated(), negVal, negated);
      }
    }
    return true;
  }

  static boolean isNaN(final DfaValue dfa) {
    return dfa instanceof DfaConstValue && DfaUtil.isNaN(((DfaConstValue)dfa).getValue());
  }

  private boolean applyRelation(@NotNull final DfaValue dfaLeft, @NotNull final DfaValue dfaRight, boolean isNegated) {
    if (isUnknownState(dfaLeft) || isUnknownState(dfaRight)) {
      return true;
    }

    // DfaConstValue || DfaVariableValue
    Integer c1Index = getOrCreateEqClassIndex(dfaLeft);
    Integer c2Index = getOrCreateEqClassIndex(dfaRight);
    if (c1Index == null || c2Index == null) {
      return true;
    }

    if (!isNegated) { //Equals
      if (c1Index.equals(c2Index) || areCompatibleConstants(c1Index, c2Index)) return true;
      if (isUnstableValue(dfaLeft) || isUnstableValue(dfaRight)) return true;
      if (!uniteClasses(c1Index, c2Index)) return false;

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
      if (c1Index.equals(c2Index) || areCompatibleConstants(c1Index, c2Index)) return false;
      if (isNull(dfaLeft) && isPrimitive(dfaRight) || isNull(dfaRight) && isPrimitive(dfaLeft)) return true;
      myDistinctClasses.addUnordered(c1Index, c2Index);
    }
    myCachedHash = null;

    return true;
  }

  private boolean applyLessThanRelation(@NotNull final DfaValue dfaLeft, @NotNull final DfaValue dfaRight) {
    if (isUnknownState(dfaLeft) || isUnknownState(dfaRight)) {
      return true;
    }

    // DfaConstValue || DfaVariableValue
    Integer c1Index = getOrCreateEqClassIndex(dfaLeft);
    Integer c2Index = getOrCreateEqClassIndex(dfaRight);
    if (c1Index == null || c2Index == null) {
      return true;
    }

    if (c1Index.equals(c2Index) || areCompatibleConstants(c1Index, c2Index)) return false;
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
    if (var.getVariableType() instanceof PsiPrimitiveType) return false;
    if (PropertyUtilBase.isSimplePropertyGetter((PsiMethod)owner)) return false;
    if (isNull(var)) return false;
    return true;
  }

  private static boolean isPrimitive(DfaValue value) {
    return value instanceof DfaVariableValue && ((DfaVariableValue)value).getVariableType() instanceof PsiPrimitiveType;
  }

  private static boolean preserveConstantDistinction(final Object c1, final Object c2) {
    return c1 == null && c2 instanceof PsiVariable ||
           c2 == null && c1 instanceof PsiVariable;
  }

  private boolean areCompatibleConstants(int i1, int i2) {
    Double dv1 = getDoubleValue(i1);
    return dv1 != null && dv1.equals(getDoubleValue(i2));
  }

  @Nullable
  private Double getDoubleValue(int eqClassIndex) {
    EqClass ec = myEqClasses.get(eqClassIndex);
    DfaValue dfaConst = ec == null ? null : ec.findConstant(false);
    Object constValue = dfaConst instanceof DfaConstValue ? ((DfaConstValue)dfaConst).getValue() : null;
    return constValue instanceof Number ? ((Number)constValue).doubleValue() : null;
  }

  boolean isUnknownState(DfaValue val) {
    val = unwrap(val);
    if (val instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue)val;
      return myUnknownVariables.contains(val) || myUnknownVariables.contains(var.getNegatedValue()) ||
             equivalentVariables(var).anyMatch(v -> myUnknownVariables.contains(v) || myUnknownVariables.contains(v.getNegatedValue()));
    }
    return false;
  }

  @Override
  public boolean checkNotNullable(DfaValue value) {
    if (value == myFactory.getConstFactory().getNull()) return false;
    if (value instanceof DfaFactMapValue && Boolean.TRUE.equals(((DfaFactMapValue)value).get(DfaFactType.CAN_BE_NULL))) return false;

    if (value instanceof DfaVariableValue) {
      DfaVariableValue varValue = (DfaVariableValue)value;
      if (varValue.getVariableType() instanceof PsiPrimitiveType) return true;
      if (isNotNull(varValue)) return true;
      return getVariableState(varValue).getNullability() != Nullability.NULLABLE;
    }
    return true;
  }

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
    if (isUnknownState(var)) return;
    DfaVariableState state = getVariableState(var);
    removeEquivalenceRelations(var);
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
    assert !myUnknownVariables.contains(dfaVar);
    if (state.equals(myDefaultVariableStates.get(dfaVar))) {
      myVariableStates.remove(dfaVar);
    } else {
      myVariableStates.put(dfaVar, state);
    }
    myCachedHash = null;
  }

  protected void updateEquivalentVariables(DfaVariableValue dfaVar, DfaVariableState state) {
    int index = getEqClassIndex(dfaVar);
    if (index != -1) {
      for (DfaValue value : myEqClasses.get(index).getMemberValues()) {
        if (value != dfaVar && value instanceof DfaVariableValue) {
          setVariableState((DfaVariableValue)value, state);
        }
      }
    }
  }

  @NotNull
  private StreamEx<DfaVariableValue> equivalentVariables(DfaVariableValue var) {
    DfaVariableValue qualifier = var.getQualifier();
    if (qualifier == null) return StreamEx.empty();
    int qualifierIndex = getEqClassIndex(qualifier);
    if (qualifierIndex == -1) return StreamEx.empty();
    return StreamEx.of(myEqClasses.get(qualifierIndex).getMemberValues())
      .without(qualifier).select(DfaVariableValue.class)
      .map(var::withQualifier);
  }

  private DfaVariableState findVariableState(DfaVariableValue var) {
    DfaVariableState state = myVariableStates.get(var);
    if (state != null) {
      return state;
    }
    return equivalentVariables(var).map(myVariableStates::get).nonNull().findFirst().orElse(null);
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
      
      if (isUnknownState(dfaVar)) {
        return state.withNotNull();
      }
    }

    return state;
  }

  void forVariableStates(BiConsumer<? super DfaVariableValue, ? super DfaVariableState> consumer) {
    myVariableStates.forEach((value, state) -> {
      if (!isUnknownState(value)) {
        consumer.accept(value, state);
      }
    });
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
      doFlush(value, shouldMarkUnknown(value));
    }
  }

  private boolean shouldMarkUnknown(@NotNull DfaVariableValue value) {
    int eqClassIndex = getEqClassIndex(value);
    if (eqClassIndex < 0) return false;

    EqClass eqClass = myEqClasses.get(eqClassIndex);
    if (eqClass == null) return false;
    DfaConstValue nullConst = myFactory.getConstFactory().getNull();
    if (eqClass.findConstant(true) == nullConst) return true;

    for (DistinctPairSet.DistinctPair pair : getDistinctClassPairs()) {
      EqClass otherClass = pair.getOtherClass(eqClassIndex);
      if (otherClass != null && otherClass.findConstant(true) == nullConst) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  Set<DfaVariableValue> getChangedVariables() {
    return myVariableStates.keySet();
  }

  @Override
  public void flushVariable(@NotNull final DfaVariableValue variable) {
    List<DfaValue> updatedStack = ContainerUtil.map(myStack, value -> handleFlush(variable, value));
    myStack.clear();
    for (DfaValue value : updatedStack) {
      myStack.push(value);
    }

    doFlush(variable, false);
    flushDependencies(variable);
    myUnknownVariables.remove(variable);
    myUnknownVariables.removeAll(variable.getDependentVariables());
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
      qualifier.getDependentVariables().stream().filter(DfaVariableValue::containsCalls)
               .forEach(val -> doFlush(val, shouldMarkUnknown(val)));
    }
  }

  @NotNull
  Set<DfaVariableValue> getUnknownVariables() {
    return myUnknownVariables;
  }

  void doFlush(@NotNull DfaVariableValue varPlain, boolean markUnknown) {
    if(isNull(varPlain)) {
      myStack.replaceAll(val -> val == varPlain ? myFactory.getConstFactory().getNull() : val);
    }
    DfaVariableValue varNegated = varPlain.getNegatedValue();

    removeEquivalenceRelations(varPlain);
    myVariableStates.remove(varPlain);
    if (varNegated != null) {
      myVariableStates.remove(varNegated);
    }
    if (markUnknown) {
      myUnknownVariables.add(varPlain);
    }
    myCachedHash = null;
  }

  void removeEquivalenceRelations(@NotNull DfaVariableValue varPlain) {
    DfaVariableValue varNegated = varPlain.getNegatedValue();
    final int idPlain = varPlain.getID();
    final int idNegated = varNegated == null ? -1 : varNegated.getID();

    int[] classes = myIdToEqClassesIndices.get(idPlain);
    int[] negatedClasses = myIdToEqClassesIndices.get(idNegated);
    int[] result = ArrayUtil
      .mergeArrays(ObjectUtils.notNull(classes, ArrayUtil.EMPTY_INT_ARRAY), ObjectUtils.notNull(negatedClasses, ArrayUtil.EMPTY_INT_ARRAY));

    int interruptCount = 0;

    for (int varClassIndex : result) {
      EqClass varClass = myEqClasses.get(varClassIndex);
      if ((++interruptCount & 0xf) == 0) {
        ProgressManager.checkCanceled();
      }

      varClass = new EqClass(varClass);
      myEqClasses.set(varClassIndex, varClass);
      for (int id : varClass.toNativeArray()) {
        int idUnwrapped;
        if (id == idPlain || id == idNegated ||
            (idUnwrapped = unwrap(myFactory.getValue(id)).getID()) == idPlain ||
            idUnwrapped == idNegated) {
          varClass.removeValue(id);
        }
      }

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
    }

    removeAllFromMap(idPlain);
    removeAllFromMap(idNegated);
    checkInvariants();
    myCachedNonTrivialEqClasses = null;
    myCachedHash = null;
  }

  private class MyIdMap extends TIntObjectHashMap<int[]> {
    private MyIdMap(int initialCapacity) {
      super(initialCapacity);
    }

    @Override
    public String toString() {
      final StringBuilder s = new StringBuilder("{");
      forEachEntry(new TIntObjectProcedure<int[]>() {
        @Override
        public boolean execute(int id, int[] set) {
          DfaValue value = myFactory.getValue(id);
          s.append(value).append(" -> ").append(Arrays.toString(set)).append(", ");
          return true;
        }
      });
      s.append("}");
      return s.toString();
    }
  }
}
