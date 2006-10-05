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
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.TIntStack;
import gnu.trove.TLongArrayList;
import gnu.trove.TLongHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DfaMemoryStateImpl implements DfaMemoryState {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DfaMemoryStateImpl");
  private ArrayList<SortedIntSet> myEqClasses;
  private int myStateSize;
  private Stack<DfaValue> myStack;
  private TIntStack myOffsetStack;
  private TLongHashSet myDistinctClasses;
  private Map<DfaVariableValue,DfaVariableState> myVariableStates;
  private DfaValueFactory myFactory;

  private DfaMemoryStateImpl(final DfaValueFactory factory) {
    myFactory = factory;
  }

  public static DfaMemoryState createEmpty(final DfaValueFactory factory) {
    DfaMemoryStateImpl empty = new DfaMemoryStateImpl(factory);

    empty.myEqClasses = new ArrayList<SortedIntSet>();
    empty.myStateSize = 0;
    empty.myStack = new Stack<DfaValue>();
    empty.myDistinctClasses = new TLongHashSet();
    empty.myVariableStates = new HashMap<DfaVariableValue, DfaVariableState>();
    empty.myOffsetStack = new TIntStack(1);
    return empty;
  }

  public DfaMemoryState createCopy() {
    DfaMemoryStateImpl newState = new DfaMemoryStateImpl(myFactory);

    newState.myStack = (Stack<DfaValue>)myStack.clone();
    newState.myDistinctClasses = new TLongHashSet(myDistinctClasses.toArray());
    newState.myEqClasses = new ArrayList<SortedIntSet>();
    newState.myStateSize = myStateSize;
    newState.myVariableStates = new HashMap<DfaVariableValue, DfaVariableState>();
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
    if (!(obj instanceof DfaMemoryState)) return false;
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
    int[] permutation = new int[size];
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
      int[] values = aClass.toNativeArray();
      for (int i = 0; i < values.length; i++) {
        if (i > 0) buf.append(", ");
        int value = values[i];
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
    long[] dclasses = myDistinctClasses.toArray();
    for (long pair : dclasses) {
      result.append("{");
      appendClass(result, low(pair));
      result.append(", ");
      appendClass(result, high(pair));
      result.append("} ");
    }

    result.append(" stack: ");
    for (int i = 0; i < myStack.size(); i++) {
      result.append(myStack.elementAt(i));
    }
    result.append('>');
    return result.toString();
  }

  public DfaValue pop() {
    return myStack.pop();
  }

  public void push(DfaValue value) {
    myStack.push(value);
  }

  public int popOffset() {
    return myOffsetStack.pop();
  }

  public void pushOffset(int offset) {
    myOffsetStack.push(offset);
  }

  public void emptyStack() {
    myStack.removeAllElements();
  }

  public void setVarValue(DfaVariableValue var, DfaValue value) {
    flushVariable(var);
    if (value instanceof DfaUnknownValue) return;

    if (value instanceof DfaNotNullValue) {
      DfaTypeValue dfaType = myFactory.getTypeFactory().create(((DfaNotNullValue)value).getType());
      DfaRelationValue dfaInstanceof = myFactory.getRelationFactory().create(var, dfaType, "instanceof", false);
      applyCondition(dfaInstanceof);
      applyCondition(compareToNull(var, true));
    }
    else if (value instanceof DfaTypeValue) {
      DfaRelationValue dfaInstanceof = myFactory.getRelationFactory().create(var, value, "instanceof", false);
      applyInstanceofOrNull(dfaInstanceof);
    }
    else {
      DfaRelationValue dfaEqual = myFactory.getRelationFactory().create(var, value, "==", false);
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

  private int getOrCreateEqClassIndex(DfaValue dfaValue) {
    int i = getEqClassIndex(dfaValue);
    if (i != -1) return i;
    SortedIntSet aClass = new SortedIntSet();
    aClass.add(dfaValue.getID());
    myEqClasses.add(aClass);
    myStateSize++;

    return myEqClasses.size() - 1;
  }

  @NotNull
  private DfaValue[] getEqClassesFor(DfaValue dfaValue) {
    int index = getEqClassIndex(dfaValue);
    List<DfaValue> result = new ArrayList<DfaValue>();
    SortedIntSet set = index == -1 ? null : myEqClasses.get(index);
    if (set != null) {
      int[] ints = set.toNativeArray();
      for (int cl : ints) {
        DfaValue value = myFactory.getValue(cl);
        result.add(value);
      }
    }
    return result.toArray(new DfaValue[result.size()]);
  }

  public  boolean canBeNaN(DfaValue dfaValue) {
    DfaValue[] eqClasses = getEqClassesFor(dfaValue);
    for (DfaValue eqClass : eqClasses) {
      if (isNaN(eqClass)) return true;
    }
    return false;
  }

  private int getEqClassIndex(@NotNull DfaValue dfaValue) {
    for (int i = 0; i < myEqClasses.size(); i++) {
      SortedIntSet aClass = myEqClasses.get(i);
      if (aClass != null && aClass.contains(dfaValue.getID())) {
        if (dfaValue instanceof DfaBoxedValue && !canBeReused(((DfaBoxedValue)dfaValue).getWrappedValue(), this) && aClass.size() > 1) return -1;
        return i;
      }
    }
    return -1;
  }
  private static boolean canBeReused(final DfaValue valueToWrap, final DfaMemoryState memoryState) {
    if (valueToWrap instanceof DfaConstValue) {
      return cacheable((DfaConstValue)valueToWrap);
    }
    else if (valueToWrap instanceof DfaVariableValue) {
      DfaValue[] values = ((DfaMemoryStateImpl)memoryState).getEqClassesFor(valueToWrap);
      for (DfaValue value : values) {
        if (value instanceof DfaConstValue && cacheable((DfaConstValue)value)) return true;
      }
      return false;
    }
    return false;
    //throw new IllegalArgumentException(""+valueToWrap);
  }

  private static boolean cacheable(DfaConstValue dfaConstValue) {
    Object value = dfaConstValue.getValue();
    return box(value) == box(value);
  }

  private static Object box(final Object value) {
    Object newBoxedValue;
    if (value instanceof Integer) newBoxedValue = Integer.valueOf(((Integer)value).intValue());
    else if (value instanceof Byte) newBoxedValue = Byte.valueOf(((Byte)value).byteValue());
    else if (value instanceof Short) newBoxedValue = Short.valueOf(((Short)value).shortValue());
    else if (value instanceof Long) newBoxedValue = Long.valueOf(((Long)value).longValue());
    else if (value instanceof Boolean) newBoxedValue = Boolean.valueOf(((Boolean)value).booleanValue());
    else if (value instanceof Character) newBoxedValue = Character.valueOf(((Character)value).charValue());
    else return new Object();
    return newBoxedValue;
  }

  private boolean uniteClasses(int c1Index, int c2Index) {
    SortedIntSet c1 = myEqClasses.get(c1Index);
    SortedIntSet c2 = myEqClasses.get(c2Index);

    int nConst = 0;
    int[] c1s = c1.toNativeArray();
    for (int c11 : c1s) {
      DfaValue dfaValue = myFactory.getValue(c11);
      if (dfaValue instanceof DfaConstValue) nConst++;
      if (dfaValue instanceof DfaBoxedValue && ((DfaBoxedValue)dfaValue).getWrappedValue() instanceof DfaConstValue) nConst++;
    }

    int[] c2s = c2.toNativeArray();
    for (int c21 : c2s) {
      DfaValue dfaValue = myFactory.getValue(c21);
      if (dfaValue instanceof DfaConstValue) nConst++;
      if (dfaValue instanceof DfaBoxedValue && ((DfaBoxedValue)dfaValue).getWrappedValue() instanceof DfaConstValue) nConst++;
    }

    if (nConst > 1) return false;

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

    c1.add(c2.toNativeArray());
    long[] c2Array = c2Pairs.toNativeArray();
    myDistinctClasses.removeAll(c2Array);
    myEqClasses.set(c2Index, null);
    myStateSize--;

    for (long l : c2Array) {
      myDistinctClasses.add(createPair(c1Index, low(l) == c2Index ? high(l) : low(l)));
    }

    return true;
  }

  private static int low(long l) {
    return (int)(l & 0xFFFFFFFF);
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
      int c1Index = getOrCreateEqClassIndex(dfaValue);
      int c2Index = getOrCreateEqClassIndex(dfaNull);

      return c1Index == c2Index;
    }

    return false;
  }

  private boolean isNotNull(DfaVariableValue dfaVar) {
    DfaConstValue dfaNull = myFactory.getConstFactory().getNull();
    int c1Index = getOrCreateEqClassIndex(dfaVar);
    int c2Index = getOrCreateEqClassIndex(dfaNull);

    long[] pairs = myDistinctClasses.toArray();
    for (long pair : pairs) {
      if (low(pair) == c1Index && high(pair) == c2Index ||
          high(pair) == c1Index && low(pair) == c2Index) {
        return true;
      }
    }

    return false;
  }

  public boolean applyInstanceofOrNull(DfaRelationValue dfaCond) {
    DfaVariableValue dfaVar = (DfaVariableValue)dfaCond.getLeftOperand();
    DfaTypeValue dfaType = (DfaTypeValue)dfaCond.getRightOperand();

    final DfaVariableState varState = getVariableState(dfaVar);
    varState.setNullable(varState.isNullable() || dfaType.isNullable());
    if (!isNotNull(dfaVar)) return true;
    return varState.setInstanceofValue(dfaType);
  }

  public boolean applyCondition(DfaValue dfaCond) {
    if (dfaCond instanceof DfaUnknownValue) return true;
    if (dfaCond instanceof DfaVariableValue) {
      DfaVariableValue dfaVar = (DfaVariableValue)dfaCond;
      boolean isNegated = dfaVar.isNegated();
      DfaVariableValue dfaNormalVar = isNegated ? (DfaVariableValue)dfaVar.createNegated() : dfaVar;
      DfaConstValue dfaTrue = myFactory.getConstFactory().getTrue();
      DfaRelationValue dfaEqualsTrue = myFactory.getRelationFactory().create(dfaNormalVar, dfaTrue, "==", isNegated);

      return applyCondition(dfaEqualsTrue);
    }

    if (dfaCond instanceof DfaConstValue) {
      if (dfaCond == myFactory.getConstFactory().getTrue()) return true;
      if (dfaCond == myFactory.getConstFactory().getFalse()) return false;
      return true;
    }

    if (!(dfaCond instanceof DfaRelationValue)) return true;

    DfaRelationValue dfaRelation = (DfaRelationValue)dfaCond;
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

    if (dfaRight instanceof DfaConstValue && dfaRight == myFactory.getConstFactory().getNull()) {
      if (dfaLeft instanceof DfaVariableValue) {
        final DfaVariableState varState = getVariableState((DfaVariableValue)dfaLeft);
        varState.setNullable(true);
      }
    }

    if (dfaLeft instanceof DfaUnknownValue || dfaRight instanceof DfaUnknownValue) return true;

    boolean result = applyRelation(dfaLeft, dfaRight, isNegated);
    if (dfaLeft instanceof DfaVariableValue) {
      PsiVariable psiVariable = ((DfaVariableValue)dfaLeft).getPsiVariable();
      if (TypeConversionUtil.isPrimitiveWrapper(psiVariable.getType())
          && (!isNegated // from the fact (wrappers are not the same) does not follow (unboxed values are not equals)
              || dfaRight instanceof DfaConstValue || dfaRight instanceof DfaBoxedValue && ((DfaBoxedValue)dfaRight).getWrappedValue() instanceof DfaConstValue)
      ){
        dfaLeft = myFactory.getBoxedFactory().createUnboxed(dfaLeft);
        dfaRight = myFactory.getBoxedFactory().createUnboxed(dfaRight);
        if (dfaLeft != null && dfaRight != null) {
          result &= applyRelation(dfaLeft, dfaRight, isNegated);
        }
      }
      else if (TypeConversionUtil.isPrimitiveAndNotNull(psiVariable.getType())){
        dfaLeft = myFactory.getBoxedFactory().createBoxed(dfaLeft);
        dfaRight = myFactory.getBoxedFactory().createBoxed(dfaRight);
        if (dfaLeft != null && dfaRight != null) {
          result &= applyRelation(dfaLeft, dfaRight, isNegated);
        }
      }
    }
    return result;
  }

  private static boolean isNaN(final DfaValue dfa) {
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
    int c1Index = getOrCreateEqClassIndex(dfaLeft);
    int c2Index = getOrCreateEqClassIndex(dfaRight);

    if (!isNegated) { //Equals
      if (c1Index == c2Index) return true;
      if (!uniteClasses(c1Index, c2Index)) return false;
    }
    else { // Not Equals
      if (c1Index == c2Index) return false;
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
    if (!checkNotNullable(value)) return false;
    return applyCondition(compareToNull(value, true));
  }

  private DfaRelationValue compareToNull(DfaValue dfaVar, boolean negated) {
    DfaConstValue dfaNull = myFactory.getConstFactory().getNull();
    return myFactory.getRelationFactory().create(dfaVar, dfaNull, "==", negated);
  }

  private DfaVariableState getVariableState(DfaVariableValue dfaVar) {
    DfaVariableState state = myVariableStates.get(dfaVar);

    if (state == null) {
      final PsiVariable psiVariable = dfaVar.getPsiVariable();
      state = new DfaVariableState(psiVariable);
      myVariableStates.put(dfaVar, state);
      if (psiVariable != null) {
        state.setInstanceofValue(myFactory.getTypeFactory().create(psiVariable.getType()));
      }
    }

    return state;
  }

  public void flushFields(DataFlowRunner runner) {
    DfaVariableValue[] fields = runner.getFields();
    for (DfaVariableValue field : fields) {
      flushVariable(field);
    }
  }

  public void flushVariable(@NotNull DfaVariableValue variable) {
    int id = variable.getID();
    for (int varClassIndex = 0; varClassIndex < myEqClasses.size(); varClassIndex++) {
      SortedIntSet varClass = myEqClasses.get(varClassIndex);
      if (varClass == null) continue;
      int[] cls = varClass.toNativeArray();
      for (int i = 0; i < cls.length; i++) {
        int cl = cls[i];
        DfaValue value = myFactory.getValue(cl);
        if (value != null && id == value.getID()
            || value instanceof DfaBoxedValue && ((DfaBoxedValue)value).getWrappedValue().getID() == id
            || value instanceof DfaUnboxedValue && ((DfaUnboxedValue)value).getVariable().getID() == id) {
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
    }

    myVariableStates.remove(variable);
  }
}
