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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UnorderedPair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.siyeh.ig.psiutils.MethodUtils;
import gnu.trove.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class DfaMemoryStateImpl implements DfaMemoryState {
  private final DfaValueFactory myFactory;

  private final List<EqClass> myEqClasses;
  // dfa value id -> indices in myEqClasses list of the classes which contain the id (or wrapped)
  private final TIntObjectHashMap<int[]> myIdToEqClassesIndices;
  private final Stack<DfaValue> myStack;
  private final TLongHashSet myDistinctClasses;
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
    myDistinctClasses = new TLongHashSet();
    myStack = new Stack<>();
    myIdToEqClassesIndices = new MyIdMap(20);
  }

  protected DfaMemoryStateImpl(DfaMemoryStateImpl toCopy) {
    myFactory = toCopy.myFactory;
    myEphemeral = toCopy.myEphemeral;
    myDefaultVariableStates = toCopy.myDefaultVariableStates; // shared between all states
    
    myStack = new Stack<>(toCopy.myStack);
    myDistinctClasses = new TLongHashSet(toCopy.myDistinctClasses.size());
    toCopy.myDistinctClasses.forEach(myDistinctClasses::add);
    myUnknownVariables = ContainerUtil.newLinkedHashSet(toCopy.myUnknownVariables);

    myEqClasses = ContainerUtil.newArrayList(toCopy.myEqClasses);
    myIdToEqClassesIndices = new MyIdMap(toCopy.myIdToEqClassesIndices.size());
    toCopy.myIdToEqClassesIndices.forEachEntry((id, set) -> {
      myIdToEqClassesIndices.put(id, set);
      return true;
    });
    myVariableStates = ContainerUtil.newLinkedHashMap(toCopy.myVariableStates);
    
    myCachedDistinctClassPairs = toCopy.myCachedDistinctClassPairs;
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
    copy.flushFields();
    Set<DfaVariableValue> vars = new HashSet<>(copy.getVariableStates().keySet());
    for (DfaVariableValue value : vars) {
      copy.flushDependencies(value);
    }
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

  private LinkedHashSet<UnorderedPair<EqClass>> myCachedDistinctClassPairs;
  LinkedHashSet<UnorderedPair<EqClass>> getDistinctClassPairs() {
    if (myCachedDistinctClassPairs != null) return myCachedDistinctClassPairs;

    LinkedHashSet<UnorderedPair<EqClass>> result = ContainerUtil.newLinkedHashSet();
    for (long encodedPair : myDistinctClasses.toArray()) {
      result.add(new UnorderedPair<>(myEqClasses.get(low(encodedPair)), myEqClasses.get(high(encodedPair))));
    }
    return myCachedDistinctClassPairs = result;
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
      String distincts =
        StreamEx.of(getDistinctClassPairs()).map(pair -> "{" + pair.first + ", " + pair.second + "}").sorted().joining(" ");
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

  @Override
  public DfaValue pop() {
    myCachedHash = null;
    return myStack.pop();
  }

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
    myCachedHash = null;
    while (!myStack.isEmpty() && !(myStack.peek() instanceof DfaControlTransferValue)) {
      myStack.pop();
    }
  }

  @Override
  public void setVarValue(DfaVariableValue var, DfaValue value) {
    if (var == value) return;

    value = handleFlush(var, value);
    flushVariable(var);

    if (value instanceof DfaUnknownValue) {
      setVariableState(var, getVariableState(var).withNotNull());
      return;
    }

    setVariableState(var, withValueNullability(value, getVariableState(var).withValue(value)));
    if (value instanceof DfaTypeValue) {
      DfaRelationValue dfaInstanceof = myFactory.getRelationFactory().createRelation(var, RelationType.IS, value);
      if (((DfaTypeValue)value).isNotNull()) {
        applyCondition(dfaInstanceof);
      } else {
        applyInstanceofOrNull(dfaInstanceof);
      }
    }
    else {
      DfaRelationValue dfaEqual = myFactory.getRelationFactory().createRelation(var, RelationType.EQ, value);
      if (dfaEqual == null) return;
      applyCondition(dfaEqual);

      if (value instanceof DfaVariableValue) {
        setVariableState(var, getVariableState((DfaVariableValue)value));
      }
    }

    if (getVariableState(var).isNotNull()) {
      DfaConstValue dfaNull = myFactory.getConstFactory().getNull();
      applyCondition(myFactory.getRelationFactory().createRelation(var, RelationType.NE, dfaNull));
    }
  }

  private DfaVariableState withValueNullability(DfaValue value, DfaVariableState state) {
    if (value instanceof DfaTypeValue) {
      return state.withFact(DfaFactType.CAN_BE_NULL, NullnessUtil.toBoolean(((DfaTypeValue)value).getNullness()));
    }
    if (isNull(value)) {
      return state.withFact(DfaFactType.CAN_BE_NULL, true);
    }
    return state;
  }

  private DfaValue handleFlush(DfaVariableValue flushed, DfaValue value) {
    if (value instanceof DfaVariableValue && (value == flushed || myFactory.getVarFactory().getAllQualifiedBy(flushed).contains(value))) {
      Nullness nullability = isNotNull(value) ? Nullness.NOT_NULL : ((DfaVariableValue)value).getInherentNullability();
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
    PsiModifierListOwner variable = variableValue.getPsiVariable();
    if (qualifier == null) return classIndex;
    Integer index = getOrCreateEqClassIndex(qualifier);
    if (index == null) return classIndex;
    for (DfaValue eqQualifier : myEqClasses.get(index).getMemberValues()) {
      if (eqQualifier != qualifier && eqQualifier instanceof DfaVariableValue) {
        DfaVariableValue eqValue = getFactory().getVarFactory()
          .createVariableValue(variable, variableValue.getVariableType(), variableValue.isNegated(), (DfaVariableValue)eqQualifier);
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
      myIdToEqClassesIndices.put(id, classes);
    }
    else {
      classes = ArrayUtil.append(classes, index);
      myIdToEqClassesIndices.put(id, classes);
    }
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
    if (!equalsSuperficially(that) ||
        !equalsByUnknownVariables(that) ||
        !getNonTrivialEqClasses().equals(that.getNonTrivialEqClasses()) ||
        !that.getDistinctClassPairs().containsAll(getDistinctClassPairs())) {
      return false;
    }
    if(myVariableStates.size() != that.myVariableStates.size()) return false;
    for (Map.Entry<DfaVariableValue, DfaVariableState> entry : myVariableStates.entrySet()) {
      DfaVariableState thisState = entry.getValue();
      DfaVariableState thatState = that.myVariableStates.get(entry.getKey());
      if(Objects.equals(thisState, thatState)) continue;
      if(thatState == null || thisState == null || !thisState.isSuperStateOf(thatState)) return false;
    }
    return true;
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

  private int getEqClassIndex(@NotNull final DfaValue dfaValue) {
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

  @SuppressWarnings("UnnecessaryBoxing")
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

    TLongArrayList c2Pairs = new TLongArrayList();
    long[] distincts = myDistinctClasses.toArray();
    for (long distinct : distincts) {
      int pc1 = low(distinct);
      int pc2 = high(distinct);
      boolean addedToC1 = false;

      if (pc1 == c1Index || pc2 == c1Index) {
        addedToC1 = true;
      }

      if (pc1 == c2Index || pc2 == c2Index) {
        if (addedToC1) return false;
        c2Pairs.add(distinct);
      }
    }

    EqClass newClass = new EqClass(c1);

    myEqClasses.set(c1Index, newClass);
    for (int i = 0; i < c2.size(); i++) {
      int c = c2.get(i);
      newClass.add(c);
      removeFromMap(c, c2Index);
      addToMap(c, c1Index);
    }

    for (int i = 0; i < c2Pairs.size(); i++) {
      long c = c2Pairs.get(i);
      myDistinctClasses.remove(c);
      myDistinctClasses.add(createPair(c1Index, low(c) == c2Index ? high(c) : low(c)));
    }
    myEqClasses.set(c2Index, null);

    return true;
  }

  private static int low(long l) {
    return (int)l;
  }

  private static int high(long l) {
    return (int)((l & 0xFFFFFFFF00000000L) >> 32);
  }

  private static long createPair(int i1, int i2) {
    if (i1 < i2) {
      long l = i1;
      l <<= 32;
      l += i2;
      return l;
    }
    else {
      long l = i2;
      l <<= 32;
      l += i1;
      return l;
    }
  }

  private void makeClassesDistinct(int c1Index, int c2Index) {
    myDistinctClasses.add(createPair(c1Index, c2Index));
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
    if (dfaVar instanceof DfaTypeValue) return ((DfaTypeValue)dfaVar).isNotNull();
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

    long[] pairs = myDistinctClasses.toArray();
    for (long pair : pairs) {
      if (c1Index == low(pair) && c2Index == high(pair) ||
          c1Index == high(pair) && c2Index == low(pair)) {
        return true;
      }
    }

    return false;
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
  public boolean applyInstanceofOrNull(@NotNull DfaRelationValue dfaCond) {
    DfaValue left = unwrap(dfaCond.getLeftOperand());

    if (!(left instanceof DfaVariableValue)) return true;

    DfaVariableValue dfaVar = (DfaVariableValue)left;
    DfaTypeValue dfaType = (DfaTypeValue)dfaCond.getRightOperand();

    if (isUnknownState(dfaVar) || isNull(dfaVar)) return true;
    DfaVariableState newState = getVariableState(dfaVar).withInstanceofValue(dfaType);
    if (newState != null) {
      setVariableState(dfaVar, newState);
      return true;
    }
    return false;
  }

  <T> void setFact(DfaValue target, DfaFactType<T> factType, T fact) {
    if (target instanceof DfaVariableValue && !isUnknownState(target)) {
      setVariableState((DfaVariableValue)target, getVariableState((DfaVariableValue)target).withFact(factType, fact));
    }
  }

  <T> boolean applyFact(DfaVariableValue target, DfaFactType<T> factType, T range) {
    if (!isUnknownState(target) && range != null) {
      DfaVariableState state = getVariableState(target);
      DfaVariableState newState = state.intersectFact(factType, range);
      if (newState == null) return false;
      setVariableState(target, newState);
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
            getVariableState((DfaVariableValue)relation.getLeftOperand()).getNullability() == Nullness.UNKNOWN))) {
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
    if (dfaLeft instanceof DfaUnknownValue || dfaRight instanceof DfaUnknownValue) return true;
    RelationType relationType = dfaRelation.getRelation();

    LongRangeSet left = getValueFact(DfaFactType.RANGE, dfaLeft);
    LongRangeSet right = getValueFact(DfaFactType.RANGE, dfaRight);

    if (left != null && right != null) {
      if (dfaLeft instanceof DfaVariableValue &&
          !applyFact((DfaVariableValue)dfaLeft, DfaFactType.RANGE, right.fromRelation(relationType))) {
        return false;
      }
      if (dfaRight instanceof DfaVariableValue &&
          !applyFact((DfaVariableValue)dfaRight, DfaFactType.RANGE, left.fromRelation(relationType.getFlipped()))) {
        return false;
      }
    }

    if (dfaLeft instanceof DfaVariableValue &&
        dfaRight instanceof DfaOptionalValue &&
        (relationType == RelationType.IS || relationType == RelationType.IS_NOT)) {
      boolean present = ((DfaOptionalValue)dfaRight).isPresent() == (relationType == RelationType.IS);
      return applyFact((DfaVariableValue)dfaLeft, DfaFactType.OPTIONAL_PRESENCE, present);
    }

    if (dfaRight instanceof DfaTypeValue) {
      if (dfaLeft instanceof DfaVariableValue) {
        DfaVariableValue dfaVar = (DfaVariableValue)dfaLeft;
        if (isUnknownState(dfaVar)) return true;

        DfaTypeValue typeValue = (DfaTypeValue)dfaRight;
        switch (relationType) {
          case EQ:
          case NE:
            return !(dfaRelation.isEquality() && typeValue.isNotNull() && isNull(dfaVar));
          case IS_NOT: {
            DfaVariableState newState = getVariableState(dfaVar).withNotInstanceofValue(typeValue);
            if (newState != null) {
              setVariableState(dfaVar, newState);
              return true;
            }
            return !getVariableState(dfaVar).isNotNull() && applyRelation(dfaVar, myFactory.getConstFactory().getNull(), false);
          }
          case IS:
            if (applyRelation(dfaVar, myFactory.getConstFactory().getNull(), true)) {
              DfaVariableState newState = getVariableState(dfaVar).withInstanceofValue(typeValue);
              if (newState != null) {
                setVariableState(dfaVar, newState);
                return true;
              }
            }
            return false;
          default:
        }
      }
      return true;
    }

    if (isEffectivelyNaN(dfaLeft) || isEffectivelyNaN(dfaRight)) {
      applyEquivalenceRelation(dfaRelation, dfaLeft, dfaRight);
      return relationType == RelationType.NE;
    }
    if (canBeNaN(dfaLeft) && canBeNaN(dfaRight)) {
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
    if (!isUnknownState(dfaVar)) {
      if (value instanceof DfaConstValue && ((DfaConstValue)value).getValue() == null) {
        setVariableState(dfaVar, getVariableState(dfaVar).withFact(DfaFactType.CAN_BE_NULL, true));
      } else if (isNotNull(value) && !isNotNull(dfaVar)) {
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

    final boolean containsCalls = dfaLeft instanceof DfaVariableValue && ((DfaVariableValue)dfaLeft).containsCalls();
    
    // track "x" property state only inside "if (getX() != null) ..."
    if (containsCalls && !isNotNull(dfaLeft) && isNull(dfaRight) && !isNegated) {
      return true;
    }
    
    if (dfaLeft == dfaRight) {
      return containsCalls || !isNegated;
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

    if (!applyRelation(dfaLeft, dfaRight, isNegated)) {
      return false;
    }
    if (!checkCompareWithBooleanLiteral(dfaLeft, dfaRight, isNegated)) {
      return false;
    }
    if (dfaLeft instanceof DfaVariableValue) {
      if (!applyUnboxedRelation((DfaVariableValue)dfaLeft, dfaRight, isNegated)) {
        return false;
      }
      if (!applyBoxedRelation((DfaVariableValue)dfaLeft, dfaRight, isNegated)) {
        return false;
      }
    }
    if (!isNegated && dfaRight instanceof DfaOptionalValue) {
      setFact(dfaLeft, DfaFactType.OPTIONAL_PRESENCE, ((DfaOptionalValue)dfaRight).isPresent());
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
        DfaConstValue negVal = myFactory.getConstFactory().createFromValue(!((Boolean)constVal).booleanValue(), PsiType.BOOLEAN, null);
        if (!applyRelation(dfaLeft, negVal, !negated)) {
          return false;
        }
        if (!applyRelation(dfaLeft.createNegated(), negVal, negated)) {
          return false;
        }
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
      if (!uniteClasses(c1Index, c2Index)) return false;

      for (long encodedPair : myDistinctClasses.toArray()) {
        EqClass c1 = myEqClasses.get(low(encodedPair));
        EqClass c2 = myEqClasses.get(high(encodedPair));
        DfaConstValue const1 = (DfaConstValue)c1.findConstant(false);
        DfaConstValue const2 = (DfaConstValue)c2.findConstant(false);
        if (const1 != null && const2 != null && !preserveConstantDistinction(const1.getValue(), const2.getValue())) {
          myDistinctClasses.remove(encodedPair);
        }
      }
      myCachedDistinctClassPairs = null;
      myCachedNonTrivialEqClasses = null;
      myCachedHash = null;
    }
    else { // Not Equals
      if (c1Index.equals(c2Index) || areCompatibleConstants(c1Index, c2Index)) return false;
      if (isNull(dfaLeft) && isPrimitive(dfaRight) || isNull(dfaRight) && isPrimitive(dfaLeft)) return true;
      makeClassesDistinct(c1Index, c2Index);
      myCachedDistinctClassPairs = null;
      myCachedHash = null;
    }

    return true;
  }

  private static boolean isPrimitive(DfaValue value) {
    return value instanceof DfaVariableValue && ((DfaVariableValue)value).getVariableType() instanceof PsiPrimitiveType;
  }

  private static boolean preserveConstantDistinction(final Object c1, final Object c2) {
    return c1 == null && c2 instanceof PsiEnumConstant ||
           c2 == null && c1 instanceof PsiEnumConstant;
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

  private boolean isUnknownState(DfaValue val) {
    val = unwrap(val);
    if (val instanceof DfaVariableValue) {
      if (myUnknownVariables.contains(val)) return true;
      DfaVariableValue negatedValue = ((DfaVariableValue)val).getNegatedValue();
      if (negatedValue != null && myUnknownVariables.contains(negatedValue)) return true;
    }
    return false;
  }

  @Override
  public boolean checkNotNullable(DfaValue value) {
    if (value == myFactory.getConstFactory().getNull()) return false;
    if (value instanceof DfaTypeValue && ((DfaTypeValue)value).isNullable()) return false;

    if (value instanceof DfaVariableValue) {
      DfaVariableValue varValue = (DfaVariableValue)value;
      if (varValue.getVariableType() instanceof PsiPrimitiveType) return true;
      if (isNotNull(varValue)) return true;
      if (getVariableState(varValue).getNullability() == Nullness.NULLABLE) return false;
    }
    return true;
  }

  @Nullable
  @SuppressWarnings("unchecked")
  public <T> T getValueFact(@NotNull DfaFactType<T> factType, @NotNull DfaValue value) {
    if (factType == DfaFactType.RANGE) {
      LongRangeSet range = getRange(value);
      if (range != null) {
        return (T)range;
      }
    }
    if (value instanceof DfaVariableValue) {
      DfaVariableState state = findVariableState((DfaVariableValue)value);
      if (state != null) {
        T fact = state.getFact(factType);
        if (fact != null) {
          return fact;
        }
      }
      DfaConstValue constValue = getConstantValue((DfaVariableValue)value);
      if (constValue != null) {
        value = constValue;
      }
    }
    return factType.fromDfaValue(value);
  }

  @Nullable
  private LongRangeSet getRange(DfaValue value) {
    if (value instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue)value;
      if (var.getPsiVariable() instanceof PsiMethod && MethodUtils.isStringLength((PsiMethod)var.getPsiVariable())) {
        DfaVariableValue qualifier = var.getQualifier();
        if(qualifier != null) {
          DfaConstValue constValue = getConstantValue(qualifier);
          if (constValue != null && constValue.getValue() instanceof String) {
            return LongRangeSet.point(((String)constValue.getValue()).length());
          }
        }
      }
    }
    return null;
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

  private DfaVariableState findVariableState(DfaVariableValue var) {
    DfaVariableState state = myVariableStates.get(var);
    if (state != null) {
      return state;
    }
    DfaVariableValue qualifier = var.getQualifier();
    if (qualifier == null) return null;
    int qualifierIndex = getEqClassIndex(qualifier);
    if (qualifierIndex == -1) return null;
    for (DfaValue eqQualifier : myEqClasses.get(qualifierIndex).getMemberValues()) {
      if (eqQualifier != qualifier && eqQualifier instanceof DfaVariableValue) {
        DfaVariableValue eqValue = getFactory().getVarFactory()
          .createVariableValue(var.getPsiVariable(), var.getVariableType(), var.isNegated(), (DfaVariableValue)eqQualifier);
        state = myVariableStates.get(eqValue);
        if (state != null) {
          return state;
        }
      }
    }
    return null;
  }
  
  DfaVariableState getVariableState(DfaVariableValue dfaVar) {
    DfaVariableState state = findVariableState(dfaVar);

    if (state == null) {
      state = myDefaultVariableStates.get(dfaVar);
      if (state == null) {
        state = createVariableState(dfaVar);
        DfaTypeValue initialType = dfaVar.getTypeValue();
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

  @NotNull
  Map<DfaVariableValue, DfaVariableState> getVariableStates() {
    return myVariableStates;
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
      if (value.isFlushableByCalls()) {
        doFlush(value, shouldMarkUnknown(value));
      }
    }
  }

  private boolean shouldMarkUnknown(@NotNull DfaVariableValue value) {
    int eqClassIndex = getEqClassIndex(value);
    if (eqClassIndex < 0) return false;

    EqClass eqClass = myEqClasses.get(eqClassIndex);
    if (eqClass == null) return false;
    if (eqClass.findConstant(true) != null) return true;

    for (UnorderedPair<EqClass> pair : getDistinctClassPairs()) {
      if (pair.first == eqClass && pair.second.findConstant(true) != null ||
          pair.second == eqClass && pair.first.findConstant(true) != null) {
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
    myUnknownVariables.removeAll(myFactory.getVarFactory().getAllQualifiedBy(variable));
    myCachedHash = null;
  }

  void flushDependencies(@NotNull DfaVariableValue variable) {
    for (DfaVariableValue dependent : myFactory.getVarFactory().getAllQualifiedBy(variable)) {
      doFlush(dependent, false);
    }
  }

  @NotNull
  Set<DfaVariableValue> getUnknownVariables() {
    return myUnknownVariables;
  }

  void doFlush(@NotNull DfaVariableValue varPlain, boolean markUnknown) {
    DfaVariableValue varNegated = varPlain.getNegatedValue();

    final int idPlain = varPlain.getID();
    final int idNegated = varNegated == null ? -1 : varNegated.getID();

    int[] classes = myIdToEqClassesIndices.get(idPlain);
    int[] negatedClasses = myIdToEqClassesIndices.get(idNegated);
    int[] result = ArrayUtil.mergeArrays(ObjectUtils.notNull(classes, ArrayUtil.EMPTY_INT_ARRAY), ObjectUtils.notNull(negatedClasses, ArrayUtil.EMPTY_INT_ARRAY));

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

        for (TLongIterator iterator = myDistinctClasses.iterator(); iterator.hasNext(); ) {
          long pair = iterator.next();
          if (low(pair) == varClassIndex || high(pair) == varClassIndex) {
            iterator.remove();
          }
        }
      }
      else if (varClass.containsConstantsOnly()) {
        for (TLongIterator iterator = myDistinctClasses.iterator(); iterator.hasNext(); ) {
          long pair = iterator.next();
          if (low(pair) == varClassIndex && myEqClasses.get(high(pair)).containsConstantsOnly() ||
              high(pair) == varClassIndex && myEqClasses.get(low(pair)).containsConstantsOnly()) {
            iterator.remove();
          }
        }
      }
    }

    removeAllFromMap(idPlain);
    removeAllFromMap(idNegated);
    myVariableStates.remove(varPlain);
    if (varNegated != null) {
      myVariableStates.remove(varNegated);
    }
    if (markUnknown) {
      myUnknownVariables.add(varPlain);
    }
    myCachedNonTrivialEqClasses = null;
    myCachedDistinctClassPairs = null;
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
