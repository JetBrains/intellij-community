/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 9:39:36 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class DfaMemoryStateImpl implements DfaMemoryState {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DfaMemoryStateImpl");
  private final DfaValueFactory myFactory;

  private final ArrayList<SortedIntSet> myEqClasses = new ArrayList<SortedIntSet>();
  private int myStateSize = 0;
  private final Stack<DfaValue> myStack = new Stack<DfaValue>();
  private TIntStack myOffsetStack = new TIntStack(1);
  private final TLongHashSet myDistinctClasses = new TLongHashSet();
  private final THashMap<DfaVariableValue,DfaVariableState> myVariableStates = new THashMap<DfaVariableValue, DfaVariableState>();

  public DfaMemoryStateImpl(final DfaValueFactory factory) {
    myFactory = factory;
  }

  public DfaValueFactory getFactory() {
    return myFactory;
  }

  protected DfaMemoryStateImpl createNew() {
    return new DfaMemoryStateImpl(myFactory);
  }

  public DfaMemoryStateImpl createCopy() {
    DfaMemoryStateImpl newState = createNew();

    //noinspection unchecked
    newState.myStack.addAll(myStack);
    newState.myDistinctClasses.addAll(myDistinctClasses.toArray());
    newState.myStateSize = myStateSize;
    newState.myOffsetStack = new TIntStack(myOffsetStack);

    for (int i = 0; i < myEqClasses.size(); i++) {
      SortedIntSet aClass = myEqClasses.get(i);
      newState.myEqClasses.add(aClass != null ? new SortedIntSet(aClass.toNativeArray()) : null);
    }

    try {
      for (Object o : myVariableStates.keySet()) {
        DfaVariableValue dfaVariableValue = (DfaVariableValue)o;
        DfaVariableState clone = (DfaVariableState)myVariableStates.get(dfaVariableValue).clone();
        newState.myVariableStates.put(dfaVariableValue, clone);
      }
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return newState;
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof DfaMemoryStateImpl)) return false;
    DfaMemoryStateImpl that = (DfaMemoryStateImpl)obj;

    if (myStateSize != that.myStateSize) return false;
    if (myDistinctClasses.size() != that.myDistinctClasses.size()) return false;

    if (!myStack.equals(that.myStack)) return false;
    if (!myOffsetStack.equals(that.myOffsetStack)) return false;
    if (!myVariableStates.equals(that.myVariableStates)) return false;

    int[] permutation = getPermutationToSortedState();
    int[] thatPermutation = that.getPermutationToSortedState();

    for (int i = 0; i < myStateSize; i++) {
      SortedIntSet thisClass = myEqClasses.get(permutation[i]);
      SortedIntSet thatClass = that.myEqClasses.get(thatPermutation[i]);
      if (thisClass == null) break;
      if (thisClass.compareTo(thatClass) != 0) return false;
    }

    long[] pairs = getSortedDistinctClasses(permutation);
    long[] thatPairs = that.getSortedDistinctClasses(thatPermutation);

    for (int i = 0; i < pairs.length; i++) {
      if (pairs[i] != thatPairs[i]) {
        return false;
      }
    }

    return true;
  }

  private long[] getSortedDistinctClasses(int[] permutation) {
    long[] pairs = myDistinctClasses.toArray();
    for (int i = 0; i < pairs.length; i++) {
      pairs[i] = convert(pairs[i], permutation);
    }
    Arrays.sort(pairs);
    return pairs;
  }

  private long convert(long pair, int[] permutation) {
    if (myEqClasses.get(low(pair)) == null || myEqClasses.get(high(pair)) == null) {
      return -1L;
    }
    return createPair(inversePermutation(permutation, low(pair)), inversePermutation(permutation, high(pair)));
  }

  private static int inversePermutation(int[] permutation, int idx) {
    for (int i = 0; i < permutation.length; i++) {
      if (idx == permutation[i]) return i;
    }
    return -1;
  }

  private int[] getPermutationToSortedState() {
    int size = myEqClasses.size();
    int[] permutation = ArrayUtil.newIntArray(size);
    for (int i = 0; i < size; i++) {
      permutation[i] = i;
    }

    for (int i = 0; i < permutation.length; i++) {
      for (int j = i + 1; j < permutation.length; j++) {
        if (compare(permutation[i], permutation[j]) > 0) {
          int t = permutation[i];
          permutation[i] = permutation[j];
          permutation[j] = t;
        }
      }
    }

    return permutation;
  }

  private int compare(int i1, int i2) {
    SortedIntSet s1 = myEqClasses.get(i1);
    SortedIntSet s2 = myEqClasses.get(i2);
    if (s1 == null && s2 == null) return 0;
    if (s1 == null) return 1;
    if (s2 == null) return -1;
    return s1.compareTo(s2);
  }

  public int hashCode() {
    return 0;
    //return myEqClasses.hashCode() + myStack.hashCode() + myVariableStates.hashCode();
  }

  private void appendClass(StringBuffer buf, int aClassIndex) {
    SortedIntSet aClass = myEqClasses.get(aClassIndex);
    if (aClass != null) {
      buf.append("(");

      for (int i = 0; i < aClass.size(); i++) {
        if (i > 0) buf.append(", ");
        int value = aClass.get(i);
        DfaValue dfaValue = myFactory.getValue(value);
        buf.append(dfaValue);
      }
      buf.append(")");
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuffer result = new StringBuffer();
    result.append('<');

    for (int i = 0; i < myEqClasses.size(); i++) {
      appendClass(result, i);
    }

    result.append(" distincts: ");
    List<String> distincs = new ArrayList<String>();
    long[] dclasses = myDistinctClasses.toArray();
    for (long pair : dclasses) {
      StringBuffer one = new StringBuffer();
      one.append("{");
      appendClass(one, low(pair));
      one.append(", ");
      appendClass(one, high(pair));
      one.append("}");
      distincs.add(one.toString());
    }
    Collections.sort(distincs);
    result.append(StringUtil.join(distincs, " "));

    result.append(" stack: ");
    for (DfaValue value : myStack) {
      result.append(value);
    }
    result.append('>');
    return result.toString();
  }

  public DfaValue pop() {
    return myStack.pop();
  }

  public DfaValue peek() {
    return myStack.peek();
  }

  public void push(@NotNull DfaValue value) {
    myStack.push(value);
  }

  public int popOffset() {
    return myOffsetStack.pop();
  }

  public void pushOffset(int offset) {
    myOffsetStack.push(offset);
  }

  public void emptyStack() {
    myStack.clear();
  }

  public void setVarValue(DfaVariableValue var, DfaValue value) {
    if (var == value) return;

    flushVariable(var);
    if (value instanceof DfaUnknownValue) {
      getVariableState(var).setNullable(false);
      return;
    }

    getVariableState(var).setValue(value);
    if (value instanceof DfaNotNullValue) {
      DfaTypeValue dfaType = myFactory.getTypeFactory().create(((DfaNotNullValue)value).getType());
      DfaRelationValue dfaInstanceof = myFactory.getRelationFactory().createRelation(var, dfaType, JavaTokenType.INSTANCEOF_KEYWORD, false);
      applyCondition(dfaInstanceof);
      applyCondition(compareToNull(var, true));
    }
    else if (value instanceof DfaTypeValue) {
      getVariableState(var).setNullable(((DfaTypeValue)value).isNullable());
      DfaRelationValue dfaInstanceof = myFactory.getRelationFactory().createRelation(var, value, JavaTokenType.INSTANCEOF_KEYWORD, false);
      applyInstanceofOrNull(dfaInstanceof);
    }
    else {
      DfaRelationValue dfaEqual = myFactory.getRelationFactory().createRelation(var, value, JavaTokenType.EQEQ, false);
      if (dfaEqual == null) return;
      applyCondition(dfaEqual);

      if (value instanceof DfaVariableValue) {
        try {
          DfaVariableState newState = (DfaVariableState)getVariableState((DfaVariableValue)value).clone();
          myVariableStates.put(var, newState);
        }
        catch (CloneNotSupportedException e) {
          LOG.error(e);
        }
      }
    }

    if (getVariableState(var).isNotNull()) {
      applyCondition(compareToNull(var, true));
    }
  }

  @Nullable("for boxed values which can't be compared by ==")
  private Integer getOrCreateEqClassIndex(DfaValue dfaValue) {
    int i = getEqClassIndex(dfaValue);
    if (i != -1) return i;
    if (!canBeReused(dfaValue) && !(((DfaBoxedValue)dfaValue).getWrappedValue() instanceof DfaConstValue)) {
      return null;
    }
    SortedIntSet aClass = new SortedIntSet();
    aClass.add(dfaValue.getID());
    myEqClasses.add(aClass);
    myStateSize++;

    return myEqClasses.size() - 1;
  }

  @NotNull
  private List<DfaValue> getEqClassesFor(@NotNull DfaValue dfaValue) {
    int index = getEqClassIndex(dfaValue);
    SortedIntSet set = index == -1 ? null : myEqClasses.get(index);
    if (set == null) {
      return Collections.emptyList();
    }
    final List<DfaValue> result = new ArrayList<DfaValue>(set.size());
    set.forEach(new TIntProcedure() {
      public boolean execute(int c1) {
        DfaValue value = myFactory.getValue(c1);
        result.add(value);
        return true;
      }
    });
    return result;
  }

  private boolean canBeNaN(@NotNull DfaValue dfaValue) {
    for (DfaValue eq : getEqClassesFor(dfaValue)) {
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
    for (DfaValue eqClass : getEqClassesFor(dfaValue)) {
      if (isNaN(eqClass)) return true;
    }
    return false;
  }

  private int getEqClassIndex(@NotNull DfaValue dfaValue) {
    for (int i = 0; i < myEqClasses.size(); i++) {
      SortedIntSet aClass = myEqClasses.get(i);
      if (aClass != null && aClass.contains(dfaValue.getID())) {
        if (!canBeReused(dfaValue) && aClass.size() > 1) return -1;
        return i;
      }
    }
    return -1;
  }

  private boolean canBeReused(final DfaValue dfaValue) {
    if (dfaValue instanceof DfaBoxedValue) {
      DfaValue valueToWrap = ((DfaBoxedValue)dfaValue).getWrappedValue();
      if (valueToWrap instanceof DfaConstValue) {
        return cacheable((DfaConstValue)valueToWrap);
      }
      if (valueToWrap instanceof DfaVariableValue) {
        if (PsiType.BOOLEAN.equals(((DfaVariableValue)valueToWrap).getVariableType())) return true;
        for (DfaValue value : getEqClassesFor(valueToWrap)) {
          if (value instanceof DfaConstValue && cacheable((DfaConstValue)value)) return true;
        }
      }
      return false;
    }
    return true;
  }

  private static boolean cacheable(DfaConstValue dfaConstValue) {
    Object value = dfaConstValue.getValue();
    return box(value) == box(value);
  }

  @SuppressWarnings({"UnnecessaryBoxing"})
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
    SortedIntSet c1 = myEqClasses.get(c1Index);
    SortedIntSet c2 = myEqClasses.get(c2Index);

    Set<DfaVariableValue> vars = ContainerUtil.newTroveSet();
    Set<DfaVariableValue> negatedVars = ContainerUtil.newTroveSet();
    int[] cs = new int[c1.size() + c2.size()];
    c1.set(0, cs, 0, c1.size());
    c2.set(0, cs, c1.size(), c2.size());

    int nConst = 0;
    for (int c : cs) {
      DfaValue dfaValue = myFactory.getValue(c);
      if (dfaValue instanceof DfaBoxedValue) dfaValue = ((DfaBoxedValue)dfaValue).getWrappedValue();
      if (dfaValue instanceof DfaUnboxedValue) dfaValue = ((DfaUnboxedValue)dfaValue).getVariable();
      if (dfaValue instanceof DfaConstValue) nConst++;
      if (dfaValue instanceof DfaVariableValue) {
        DfaVariableValue variableValue = (DfaVariableValue)dfaValue;
        if (variableValue.isNegated()) {
          negatedVars.add(variableValue.createNegated());
        } else {
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

    for (int i = 0; i < c2.size(); i++) {
      int c = c2.get(i);
      c1.add(c);
    }

    for (int i = 0; i < c2Pairs.size(); i++) {
      long c = c2Pairs.get(i);
      myDistinctClasses.remove(c);
      myDistinctClasses.add(createPair(c1Index, low(c) == c2Index ? high(c) : low(c)));
    }
    myEqClasses.set(c2Index, null);
    myStateSize--;

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

  public boolean isNull(DfaValue dfaValue) {
    if (dfaValue instanceof DfaNotNullValue) return false;

    if (dfaValue instanceof DfaVariableValue || dfaValue instanceof DfaConstValue) {
      DfaConstValue dfaNull = myFactory.getConstFactory().getNull();
      Integer c1Index = getOrCreateEqClassIndex(dfaValue);
      Integer c2Index = getOrCreateEqClassIndex(dfaNull);

      return c1Index != null && c1Index.equals(c2Index);
    }

    return false;
  }

  public boolean isNotNull(DfaVariableValue dfaVar) {
    if (getVariableState(dfaVar).isNotNull()) {
      return true;
    }

    DfaConstValue dfaNull = myFactory.getConstFactory().getNull();
    Integer c1Index = getOrCreateEqClassIndex(dfaVar);
    Integer c2Index = getOrCreateEqClassIndex(dfaNull);
    if (c1Index == null || c2Index == null) {
      return false;
    }

    long[] pairs = myDistinctClasses.toArray();
    for (long pair : pairs) {
      if (c1Index.equals(low(pair)) && c2Index.equals(high(pair)) ||
          c1Index.equals(high(pair)) && c2Index.equals(low(pair))) {
        return true;
      }
    }

    return false;
  }

  public boolean applyInstanceofOrNull(DfaRelationValue dfaCond) {
    DfaValue left = dfaCond.getLeftOperand();
    if (left instanceof DfaBoxedValue) {
      left = ((DfaBoxedValue)left).getWrappedValue();
    }
    else if (left instanceof DfaUnboxedValue) {
      left = ((DfaUnboxedValue)left).getVariable();
    }

    if (!(left instanceof DfaVariableValue)) return true;

    DfaVariableValue dfaVar = (DfaVariableValue)left;
    DfaTypeValue dfaType = (DfaTypeValue)dfaCond.getRightOperand();

    final DfaVariableState varState = getVariableState(dfaVar);
    varState.setNullable(varState.isNullable() || dfaType.isNullable());
    return !isNotNull(dfaVar) || varState.setInstanceofValue(dfaType);
  }

  public boolean applyCondition(DfaValue dfaCond) {
    if (dfaCond instanceof DfaUnknownValue) return true;
    if (dfaCond instanceof DfaUnboxedValue) {
      DfaVariableValue dfaVar = ((DfaUnboxedValue)dfaCond).getVariable();
      boolean isNegated = dfaVar.isNegated();
      DfaVariableValue dfaNormalVar = isNegated ? dfaVar.createNegated() : dfaVar;
      final DfaValue boxedTrue = myFactory.getBoxedFactory().createBoxed(myFactory.getConstFactory().getTrue());
      return applyRelationCondition(myFactory.getRelationFactory().createRelation(dfaNormalVar, boxedTrue, JavaTokenType.EQEQ, isNegated));
    }
    if (dfaCond instanceof DfaVariableValue) {
      DfaVariableValue dfaVar = (DfaVariableValue)dfaCond;
      boolean isNegated = dfaVar.isNegated();
      DfaVariableValue dfaNormalVar = isNegated ? dfaVar.createNegated() : dfaVar;
      DfaConstValue dfaTrue = myFactory.getConstFactory().getTrue();
      return applyRelationCondition(myFactory.getRelationFactory().createRelation(dfaNormalVar, dfaTrue, JavaTokenType.EQEQ, isNegated));
    }

    if (dfaCond instanceof DfaConstValue) {
      return dfaCond == myFactory.getConstFactory().getTrue() || dfaCond != myFactory.getConstFactory().getFalse();
    }

    if (!(dfaCond instanceof DfaRelationValue)) return true;

    return applyRelationCondition((DfaRelationValue)dfaCond);
  }

  private boolean applyRelationCondition(DfaRelationValue dfaRelation) {
    DfaValue dfaLeft = dfaRelation.getLeftOperand();
    DfaValue dfaRight = dfaRelation.getRightOperand();
    if (dfaRight == null || dfaLeft == null) return false;

    boolean isNegated = dfaRelation.isNegated();
    if (dfaLeft instanceof DfaNotNullValue && dfaRight == myFactory.getConstFactory().getNull()) {
      return isNegated;
    }

    if (dfaRight instanceof DfaTypeValue) {
      if (dfaLeft instanceof DfaVariableValue) {
        DfaVariableState varState = getVariableState((DfaVariableValue)dfaLeft);
        DfaVariableValue dfaVar = (DfaVariableValue)dfaLeft;
        if (isNegated) {
          return varState.addNotInstanceofValue((DfaTypeValue)dfaRight) || applyCondition(compareToNull(dfaVar, false));
        }
        return applyCondition(compareToNull(dfaVar, true)) && varState.setInstanceofValue((DfaTypeValue)dfaRight);
      }
      return true;
    }

    if (dfaRight instanceof DfaNotNullValue) {
      return true;
    }

    if (isNull(dfaRight) && compareVariableWithNull(dfaLeft) || isNull(dfaLeft) && compareVariableWithNull(dfaRight)) {
      return isNegated;
    }

    if (dfaLeft instanceof DfaUnknownValue || dfaRight instanceof DfaUnknownValue) return true;


    if (isEffectivelyNaN(dfaLeft) || isEffectivelyNaN(dfaRight)) {
      applyEquivalenceRelation(dfaRelation, dfaLeft, dfaRight);
      return isNegated;
    }
    if (canBeNaN(dfaLeft) || canBeNaN(dfaRight)) {
      applyEquivalenceRelation(dfaRelation, dfaLeft, dfaRight);
      return true;
    }

    return applyEquivalenceRelation(dfaRelation, dfaLeft, dfaRight);
  }

  private boolean compareVariableWithNull(DfaValue val) {
    if (val instanceof DfaVariableValue) {
      DfaVariableValue dfaVar = (DfaVariableValue)val;
      if (isNotNull(dfaVar)) {
        return true;
      }
      getVariableState(dfaVar).setNullable(true);
    }
    return false;
  }

  private boolean applyEquivalenceRelation(DfaRelationValue dfaRelation, DfaValue dfaLeft, DfaValue dfaRight) {
    boolean isNegated = dfaRelation.isNonEquality();
    if (!isNegated && !dfaRelation.isEquality()) {
      return true;
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

    return true;
  }

  private boolean applyBoxedRelation(DfaVariableValue dfaLeft, DfaValue dfaRight, boolean negated) {
    if (!TypeConversionUtil.isPrimitiveAndNotNull(dfaLeft.getVariableType())) return true;

    DfaBoxedValue.Factory boxedFactory = myFactory.getBoxedFactory();
    DfaValue boxedLeft = boxedFactory.createBoxed(dfaLeft);
    DfaValue boxedRight = boxedFactory.createBoxed(dfaRight);
    return boxedLeft == null || boxedRight == null || applyRelation(boxedLeft, boxedRight, negated);
  }

  private boolean applyUnboxedRelation(DfaVariableValue dfaLeft, DfaValue dfaRight, boolean negated) {
    PsiType type = dfaLeft.getVariableType();
    if (!TypeConversionUtil.isPrimitiveWrapper(type)) {
      return true;
    }
    if (negated && !isMaybeBoxedConstant(dfaRight)) {
      // from the fact (wrappers are not the same) does not follow (unboxed values are not equals)
      return true;
    }

    DfaBoxedValue.Factory boxedFactory = myFactory.getBoxedFactory();
    return applyRelation(boxedFactory.createUnboxed(dfaLeft), boxedFactory.createUnboxed(dfaRight), negated);
  }

  private static boolean isMaybeBoxedConstant(DfaValue val) {
    return val instanceof DfaConstValue ||
           (val instanceof DfaBoxedValue && ((DfaBoxedValue)val).getWrappedValue() instanceof DfaConstValue);
  }

  private boolean checkCompareWithBooleanLiteral(DfaValue dfaLeft, DfaValue dfaRight, boolean negated) {
    if (dfaRight instanceof DfaConstValue) {
      Object constVal = ((DfaConstValue)dfaRight).getValue();
      if (constVal instanceof Boolean) {
        DfaConstValue negVal = myFactory.getConstFactory().createFromValue(!((Boolean)constVal).booleanValue(), PsiType.BOOLEAN);
        if (!applyRelation(dfaLeft, negVal, !negated)) {
          return false;
        }
      }
    }
    return true;
  }

  static boolean isNaN(final DfaValue dfa) {
    if (dfa instanceof DfaConstValue) {
      Object value = ((DfaConstValue)dfa).getValue();
      if (value instanceof Double && ((Double)value).isNaN()) return true;
      if (value instanceof Float && ((Float)value).isNaN()) return true;
    }
    else if (dfa instanceof DfaBoxedValue){
      return isNaN(((DfaBoxedValue)dfa).getWrappedValue());
    }
    return false;
  }

  private boolean applyRelation(@NotNull final DfaValue dfaLeft, @NotNull final DfaValue dfaRight, boolean isNegated) {
    // DfaConstValue || DfaVariableValue
    Integer c1Index = getOrCreateEqClassIndex(dfaLeft);
    Integer c2Index = getOrCreateEqClassIndex(dfaRight);
    if (c1Index == null || c2Index == null) {
      return true;
    }

    if (!isNegated) { //Equals
      if (c1Index.equals(c2Index)) return true;
      if (!uniteClasses(c1Index, c2Index)) return false;
    }
    else { // Not Equals
      if (c1Index.equals(c2Index)) return false;
      makeClassesDistinct(c1Index, c2Index);
    }

    return true;
  }

  public boolean checkNotNullable(DfaValue value) {
    if (value == myFactory.getConstFactory().getNull()) return false;
    if (value instanceof DfaTypeValue && ((DfaTypeValue)value).isNullable()) return false;

    if (value instanceof DfaVariableValue) {
      if (isNotNull((DfaVariableValue)value)) return true;
      final DfaVariableState varState = getVariableState((DfaVariableValue)value);
      if (varState.isNullable()) return false;
    }
    return true;
  }

  public boolean applyNotNull(DfaValue value) {
    if (value instanceof DfaVariableValue && ((DfaVariableValue)value).getVariableType() instanceof PsiPrimitiveType) {
      return true;
    }

    return checkNotNullable(value) && applyCondition(compareToNull(value, true));
  }

  @Nullable
  private DfaRelationValue compareToNull(DfaValue dfaVar, boolean negated) {
    DfaConstValue dfaNull = myFactory.getConstFactory().getNull();
    return myFactory.getRelationFactory().createRelation(dfaVar, dfaNull, JavaTokenType.EQEQ, negated);
  }

  public DfaVariableState getVariableState(DfaVariableValue dfaVar) {
    DfaVariableState state = myVariableStates.get(dfaVar);

    if (state == null) {
      state = createVariableState(dfaVar);
      myVariableStates.put(dfaVar, state);
      PsiType type = dfaVar.getVariableType();
      if (type != null) {
        state.setInstanceofValue(myFactory.getTypeFactory().create(type));
      }
    }

    return state;
  }

  protected Map<DfaVariableValue, DfaVariableState> getVariableStates() {
    return myVariableStates;
  }

  protected DfaVariableState createVariableState(final DfaVariableValue var) {
    return new DfaVariableState(var);
  }

  public void flushFields(DataFlowRunner runner) {
    for (DfaVariableValue field : runner.getFields()) {
      if (myVariableStates.containsKey(field) || getEqClassIndex(field) >= 0) {
        flushVariable(field);
        getVariableState(field).setNullable(false);
      }
    }
  }

  public void flushVariable(@NotNull DfaVariableValue variable) {
    PsiVariable psiVariable = variable.getPsiVariable();
    if (DfaVariableState.isFinalField(psiVariable)) {
      return;
    }

    flushWithDependencies(variable);
  }

  @Override
  public void flushVariableOutOfScope(DfaVariableValue variable) {
    flushWithDependencies(variable);
  }

  private void flushWithDependencies(DfaVariableValue variable) {
    doFlush(variable);
    for (DfaVariableValue dependent : myFactory.getVarFactory().getAllQualifiedBy(variable)) {
      doFlush(dependent);
    }
  }

  private void doFlush(DfaVariableValue varPlain) {
    DfaVariableValue varNegated = varPlain.createNegated();

    final int idPlain = varPlain.getID();
    final int idNegated = varNegated.getID();

    int size = myEqClasses.size();
    int interruptCount = 0;
    for (int varClassIndex = 0; varClassIndex < size; varClassIndex++) {
      final SortedIntSet varClass = myEqClasses.get(varClassIndex);
      if (varClass == null) continue;

      for (int i = 0; i < varClass.size(); i++) {
        if ((++interruptCount & 0xf) == 0) {
          ProgressManager.checkCanceled();
        }
        int cl = varClass.get(i);
        DfaValue value = myFactory.getValue(cl);
        if (mine(idPlain, value) || mine(idNegated, value)) {
          varClass.remove(i);
          break;
        }
      }

      if (varClass.isEmpty()) {
        myEqClasses.set(varClassIndex, null);
        myStateSize--;
        long[] pairs = myDistinctClasses.toArray();
        for (long pair : pairs) {
          if (low(pair) == varClassIndex || high(pair) == varClassIndex) {
            myDistinctClasses.remove(pair);
          }
        }
      }
      else if (containsConstantsOnly(varClassIndex)) {
        for (long pair : myDistinctClasses.toArray()) {
          if (low(pair) == varClassIndex && containsConstantsOnly(high(pair)) ||
              high(pair) == varClassIndex && containsConstantsOnly(low(pair))) {
            myDistinctClasses.remove(pair);
          }
        }
      }
    }

    myVariableStates.remove(varPlain);
    myVariableStates.remove(varNegated);
  }

  private boolean containsConstantsOnly(int id) {
    SortedIntSet varClass = myEqClasses.get(id);
    for (int i = 0; i < varClass.size(); i++) {
      int cl = varClass.get(i);
      DfaValue value = myFactory.getValue(cl);
      if (!(value instanceof DfaConstValue) &&
          !(value instanceof DfaBoxedValue && ((DfaBoxedValue)value).getWrappedValue() instanceof DfaConstValue)) {
        return false;
      }
    }

    return true;
  }

  private static boolean mine(int id, DfaValue value) {
    return value != null && id == value.getID() ||
        value instanceof DfaBoxedValue && ((DfaBoxedValue)value).getWrappedValue().getID() == id ||
        value instanceof DfaUnboxedValue && ((DfaUnboxedValue)value).getVariable().getID() == id;
  }
}
