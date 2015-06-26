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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UnorderedPair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import gnu.trove.TLongArrayList;
import gnu.trove.TLongHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class DfaMemoryStateImpl implements DfaMemoryState {
  private final DfaValueFactory myFactory;

  private final List<EqClass> myEqClasses;
  private final Stack<DfaValue> myStack;
  private final TLongHashSet myDistinctClasses;
  private final LinkedHashMap<DfaVariableValue,DfaVariableState> myVariableStates;
  private final Map<DfaVariableValue,DfaVariableState> myDefaultVariableStates; 
  private final LinkedHashSet<DfaVariableValue> myUnknownVariables;
  private boolean myEphemeral;

  public DfaMemoryStateImpl(final DfaValueFactory factory) {
    myFactory = factory;
    myDefaultVariableStates = ContainerUtil.newTroveMap();
    myEqClasses = ContainerUtil.newArrayList();
    myUnknownVariables = ContainerUtil.newLinkedHashSet();
    myVariableStates = ContainerUtil.newLinkedHashMap();
    myDistinctClasses = new TLongHashSet();
    myStack = new Stack<DfaValue>();
  }

  protected DfaMemoryStateImpl(DfaMemoryStateImpl toCopy) {
    myFactory = toCopy.myFactory;
    myEphemeral = toCopy.myEphemeral;
    myDefaultVariableStates = toCopy.myDefaultVariableStates; // shared between all states
    
    myStack = new Stack<DfaValue>(toCopy.myStack);
    myDistinctClasses = new TLongHashSet(toCopy.myDistinctClasses.toArray());
    myUnknownVariables = ContainerUtil.newLinkedHashSet(toCopy.myUnknownVariables);

    myEqClasses = ContainerUtil.newArrayList(toCopy.myEqClasses);
    myVariableStates = ContainerUtil.newLinkedHashMap(toCopy.myVariableStates);
    
    myCachedDistinctClassPairs = toCopy.myCachedDistinctClassPairs;
    myCachedNonTrivialEqClasses = toCopy.myCachedNonTrivialEqClasses;
    myCachedHash = toCopy.myCachedHash;
  }

  public DfaValueFactory getFactory() {
    return myFactory;
  }

  @Override
  public DfaMemoryStateImpl createCopy() {
    return new DfaMemoryStateImpl(this);
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof DfaMemoryStateImpl)) return false;
    DfaMemoryStateImpl that = (DfaMemoryStateImpl)obj;
    if (myCachedHash != null && that.myCachedHash != null && !myCachedHash.equals(that.myCachedHash)) return false;
    return equalsSuperficially(that) && equalsByUnknownVariables(that) && equalsByRelations(that) && equalsByVariableStates(that);
  }

  boolean equalsByUnknownVariables(DfaMemoryStateImpl that) {
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
      result.add(new UnorderedPair<EqClass>(myEqClasses.get(low(encodedPair)), myEqClasses.get(high(encodedPair))));
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

  @SuppressWarnings({"HardCodedStringLiteral"})
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
      List<String> distincts = new ArrayList<String>();
      for (UnorderedPair<EqClass> pair : getDistinctClassPairs()) {
        distincts.add("{" + pair.first + ", " + pair.second + "}");
      }
      Collections.sort(distincts);
      result.append(StringUtil.join(distincts, " "));
    }

    if (!myStack.isEmpty()) {
      result.append("\n  stack: ").append(StringUtil.join(myStack, ","));
    }
    if (!myVariableStates.isEmpty()) {
      result.append("\n  vars: ");
      for (Map.Entry<DfaVariableValue, DfaVariableState> entry : myVariableStates.entrySet()) {
        result.append("[").append(entry.getKey()).append("->").append(entry.getValue()).append("] ");
      }
    }
    if (!myUnknownVariables.isEmpty()) {
      result.append("\n  unknowns: ").append(new HashSet<DfaVariableValue>(myUnknownVariables));
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
    myStack.clear();
  }

  @Override
  public void setVarValue(DfaVariableValue var, DfaValue value) {
    if (var == value) return;

    flushVariable(var);
    if (value instanceof DfaUnknownValue) {
      setVariableState(var, getVariableState(var).withNullable(false));
      return;
    }

    setVariableState(var, getVariableState(var).withValue(value));
    if (value instanceof DfaTypeValue) {
      setVariableState(var, getVariableState(var).withNullable(((DfaTypeValue)value).isNullable()));
      DfaRelationValue dfaInstanceof = myFactory.getRelationFactory().createRelation(var, value, JavaTokenType.INSTANCEOF_KEYWORD, false);
      if (((DfaTypeValue)value).isNotNull()) {
        applyCondition(dfaInstanceof);
      } else {
        applyInstanceofOrNull(dfaInstanceof);
      }
    }
    else {
      DfaRelationValue dfaEqual = myFactory.getRelationFactory().createRelation(var, value, JavaTokenType.EQEQ, false);
      if (dfaEqual == null) return;
      applyCondition(dfaEqual);

      if (value instanceof DfaVariableValue) {
        setVariableState(var, getVariableState((DfaVariableValue)value));
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
    if (!canBeInRelation(dfaValue) ||
        !canBeReused(dfaValue) && !(((DfaBoxedValue)dfaValue).getWrappedValue() instanceof DfaConstValue)) {
      return null;
    }
    EqClass aClass = new EqClass(myFactory);
    aClass.add(dfaValue.getID());

    int freeIndex = myEqClasses.indexOf(null);
    if (freeIndex >= 0) {
      myEqClasses.set(freeIndex, aClass);
      return freeIndex;
    }

    myEqClasses.add(aClass);
    return myEqClasses.size() - 1;
  }

  private static boolean canBeInRelation(DfaValue dfaValue) {
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

  private int getEqClassIndex(@NotNull DfaValue dfaValue) {
    for (int i = 0; i < myEqClasses.size(); i++) {
      EqClass aClass = myEqClasses.get(i);
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
        for (DfaValue value : getEquivalentValues(valueToWrap)) {
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

    myEqClasses.set(c1Index, c1 = new EqClass(c1));
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
    int c1Index = getEqClassIndex(dfaVar);
    int c2Index = getEqClassIndex(dfaNull);
    if (c1Index < 0 || c2Index < 0) {
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
  public DfaConstValue getConstantValue(DfaVariableValue value) {
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
  public boolean applyInstanceofOrNull(DfaRelationValue dfaCond) {
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

  static DfaValue unwrap(DfaValue value) {
    if (value instanceof DfaBoxedValue) {
      return ((DfaBoxedValue)value).getWrappedValue();
    }
    else if (value instanceof DfaUnboxedValue) {
      return ((DfaUnboxedValue)value).getVariable();
    }
    return value;
  }

  @Override
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
    if (dfaLeft instanceof DfaUnknownValue || dfaRight instanceof DfaUnknownValue) return true;

    boolean isNegated = dfaRelation.isNegated();
    if (dfaLeft instanceof DfaTypeValue && ((DfaTypeValue)dfaLeft).isNotNull() && dfaRight == myFactory.getConstFactory().getNull()) {
      return isNegated;
    }

    if (dfaRight instanceof DfaTypeValue) {
      if (dfaLeft instanceof DfaVariableValue) {
        DfaVariableValue dfaVar = (DfaVariableValue)dfaLeft;
        if (isUnknownState(dfaVar)) return true;

        if (!dfaRelation.isInstanceOf()) {
          if (((DfaTypeValue)dfaRight).isNotNull() && isNull(dfaVar)) {
            return isNegated;
          }
          return true;
        }

        if (isNegated) {
          DfaVariableState newState = getVariableState(dfaVar).withNotInstanceofValue((DfaTypeValue)dfaRight);
          if (newState != null) {
            setVariableState(dfaVar, newState);
            return true;
          }
          return applyRelation(dfaVar, myFactory.getConstFactory().getNull(), false);
        }
        if (applyRelation(dfaVar, myFactory.getConstFactory().getNull(), true)) {
          DfaVariableState newState = getVariableState(dfaVar).withInstanceofValue((DfaTypeValue)dfaRight);
          if (newState != null) {
            setVariableState(dfaVar, newState);
            return true;
          }
        }
        return false;
      }
      return true;
    }

    if (isEffectivelyNaN(dfaLeft) || isEffectivelyNaN(dfaRight)) {
      applyEquivalenceRelation(dfaRelation, dfaLeft, dfaRight);
      return isNegated;
    }
    if (canBeNaN(dfaLeft) && canBeNaN(dfaRight)) {
      applyEquivalenceRelation(dfaRelation, dfaLeft, dfaRight);
      return true;
    }

    return applyEquivalenceRelation(dfaRelation, dfaLeft, dfaRight);
  }

  private void updateVarStateOnComparison(DfaVariableValue dfaVar, DfaValue value) {
    if (!isUnknownState(dfaVar)) {
      if (value instanceof DfaConstValue && ((DfaConstValue)value).getValue() == null) {
        setVariableState(dfaVar, getVariableState(dfaVar).withNullability(Nullness.NULLABLE));
      } else if (isNotNull(value) && !isNotNull(dfaVar)) {
        setVariableState(dfaVar, getVariableState(dfaVar).withNullability(Nullness.UNKNOWN));
        applyRelation(dfaVar, myFactory.getConstFactory().getNull(), true);
      }
    }
  }

  private boolean applyEquivalenceRelation(DfaRelationValue dfaRelation, DfaValue dfaLeft, DfaValue dfaRight) {
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
    if (negated) {
      // from the fact "wrappers are not the same" it does not follow that "unboxed values are not equal"
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
      if (c1Index.equals(c2Index)) return true;
      if (!uniteClasses(c1Index, c2Index)) return false;

      for (long encodedPair : myDistinctClasses.toArray()) {
        EqClass c1 = myEqClasses.get(low(encodedPair));
        EqClass c2 = myEqClasses.get(high(encodedPair));
        if (c1.findConstant(false) != null && c2.findConstant(false) != null) {
          myDistinctClasses.remove(encodedPair);
        }
      }
      myCachedDistinctClassPairs = null;
      myCachedNonTrivialEqClasses = null;
      myCachedHash = null;
    }
    else { // Not Equals
      if (c1Index.equals(c2Index)) return false;
      makeClassesDistinct(c1Index, c2Index);
      myCachedDistinctClassPairs = null;
      myCachedHash = null;
    }

    return true;
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
      if (getVariableState(varValue).isNullable()) return false;
    }
    return true;
  }

  @Nullable
  private DfaRelationValue compareToNull(DfaValue dfaVar, boolean negated) {
    DfaConstValue dfaNull = myFactory.getConstFactory().getNull();
    return myFactory.getRelationFactory().createRelation(dfaVar, dfaNull, JavaTokenType.EQEQ, negated);
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
  
  public DfaVariableState getVariableState(DfaVariableValue dfaVar) {
    DfaVariableState state = myVariableStates.get(dfaVar);

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
        return state.withNullable(false);
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

  private boolean shouldMarkUnknown(DfaVariableValue value) {
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

  Set<DfaVariableValue> getChangedVariables() {
    return myVariableStates.keySet();
  }

  @Override
  public void flushVariable(@NotNull DfaVariableValue variable) {
    doFlush(variable, false);
    flushDependencies(variable);
    myUnknownVariables.remove(variable);
    myUnknownVariables.removeAll(myFactory.getVarFactory().getAllQualifiedBy(variable));
    myCachedHash = null;
  }

  public void flushDependencies(DfaVariableValue variable) {
    for (DfaVariableValue dependent : myFactory.getVarFactory().getAllQualifiedBy(variable)) {
      doFlush(dependent, false);
    }
  }

  Set<DfaVariableValue> getUnknownVariables() {
    return myUnknownVariables;
  }

  void doFlush(DfaVariableValue varPlain, boolean markUnknown) {
    DfaVariableValue varNegated = varPlain.getNegatedValue();

    final int idPlain = varPlain.getID();
    final int idNegated = varNegated == null ? -1 : varNegated.getID();

    int size = myEqClasses.size();
    int interruptCount = 0;
    for (int varClassIndex = 0; varClassIndex < size; varClassIndex++) {
      EqClass varClass = myEqClasses.get(varClassIndex);
      if (varClass == null) continue;

      for (int i = 0; i < varClass.size(); i++) {
        if ((++interruptCount & 0xf) == 0) {
          ProgressManager.checkCanceled();
        }
        int cl = varClass.get(i);
        DfaValue value = myFactory.getValue(cl);
        if (mine(idPlain, value) || idNegated >= 0 && mine(idNegated, value)) {
          myEqClasses.set(varClassIndex, varClass = new EqClass(varClass));
          varClass.remove(i);
          break;
        }
      }

      if (varClass.isEmpty()) {
        myEqClasses.set(varClassIndex, null);
        long[] pairs = myDistinctClasses.toArray();
        for (long pair : pairs) {
          if (low(pair) == varClassIndex || high(pair) == varClassIndex) {
            myDistinctClasses.remove(pair);
          }
        }
      }
      else if (varClass.containsConstantsOnly()) {
        for (long pair : myDistinctClasses.toArray()) {
          if (low(pair) == varClassIndex && myEqClasses.get(high(pair)).containsConstantsOnly() ||
              high(pair) == varClassIndex && myEqClasses.get(low(pair)).containsConstantsOnly()) {
            myDistinctClasses.remove(pair);
          }
        }
      }
    }

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

  private static boolean mine(int id, DfaValue value) {
    return value != null && id == unwrap(value).getID();
  }
}
