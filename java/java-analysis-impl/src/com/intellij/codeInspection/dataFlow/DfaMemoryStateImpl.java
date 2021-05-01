// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.jvm.JvmSpecialField;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.memory.EqClass;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import it.unimi.dsi.fastutil.ints.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Invariant: qualifiers of the variables used in myEqClasses or myVariableTypes must be canonical variables
 * where canonical variable is the minimal DfaVariableValue inside its eqClass, according to EqClass#CANONICAL_VARIABLE_COMPARATOR.
 */
public class DfaMemoryStateImpl implements DfaMemoryState {
  private static final Logger LOG = Logger.getInstance(DfaMemoryStateImpl.class);

  private final DfaValueFactory myFactory;

  private final List<EqClass> myEqClasses;
  // dfa value id -> indices in myEqClasses list of the classes which contain the id
  private final Int2IntMap myIdToEqClassesIndices;
  private final Stack<DfaValue> myStack;
  private final DistinctPairSet myDistinctClasses;
  private final LinkedHashMap<DfaVariableValue,DfType> myVariableTypes;
  private boolean myEphemeral;

  protected DfaMemoryStateImpl(final DfaValueFactory factory) {
    myFactory = factory;
    myEqClasses = new ArrayList<>();
    myVariableTypes = new LinkedHashMap<>();
    myDistinctClasses = new DistinctPairSet(this);
    myStack = new Stack<>();
    myIdToEqClassesIndices = new Int2IntOpenHashMap();
  }

  protected DfaMemoryStateImpl(DfaMemoryStateImpl toCopy) {
    myFactory = toCopy.myFactory;
    myEphemeral = toCopy.myEphemeral;

    myStack = new Stack<>(toCopy.myStack);
    myDistinctClasses = new DistinctPairSet(this, toCopy.myDistinctClasses);

    myEqClasses = new ArrayList<>(toCopy.myEqClasses);
    myIdToEqClassesIndices = new Int2IntOpenHashMap(toCopy.myIdToEqClassesIndices);
    myVariableTypes = new LinkedHashMap<>(toCopy.myVariableTypes);

    myCachedNonTrivialEqClasses = toCopy.myCachedNonTrivialEqClasses;
    myCachedHash = toCopy.myCachedHash;
  }

  public @NotNull DfaValueFactory getFactory() {
    return myFactory;
  }

  @Override
  public @NotNull DfaMemoryStateImpl createCopy() {
    return new DfaMemoryStateImpl(this);
  }

  @Override
  public @NotNull DfaMemoryStateImpl createClosureState() {
    DfaMemoryStateImpl copy = createCopy();
    copy.flushFields();
    copy.emptyStack();
    for (DfaValue value : getFactory().getValues().toArray(new DfaValue[0])) {
      if (value instanceof DfaVariableValue) {
        DfType type = copy.getDfType(value);
        DfType newType = type.correctForClosure();
        if (newType != type) {
          copy.setDfType(value, newType);
        }
      }
    }
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
           myVariableTypes.equals(that.myVariableTypes);
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

    LinkedHashSet<EqClass> result = new LinkedHashSet<>();
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
                 myStack.hashCode()) * 31 + myVariableTypes.hashCode();
    return myCachedHash = hash;
  }

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
    if (!myVariableTypes.isEmpty()) {
      result.append("\n  vars: ");
      myVariableTypes.forEach((key, value) -> result.append("[").append(key).append("->").append(value).append("] "));
    }
    result.append('>');
    return result.toString();
  }

  @Override
  public @NotNull DfaValue pop() {
    myCachedHash = null;
    return myStack.pop();
  }

  @Override
  public @NotNull DfaValue peek() {
    return myStack.peek();
  }

  @Override
  public @Nullable DfaValue getStackValue(int offset) {
    int index = myStack.size() - 1 - offset;
    return index < 0 ? null : myStack.get(index);
  }

  @Override
  public void push(@NotNull DfaValue value) {
    assert value.getFactory() == myFactory;
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
    assert value.getFactory() == myFactory;
    assert var.getFactory() == myFactory;
    if (var == value) return;

    value = handleStackValueOnVariableFlush(value, var, null);
    flushVariable(var, DfaNullability.fromDfType(var.getInherentType()) != DfaNullability.UNKNOWN);
    flushQualifiedMethods(var);

    if (DfaTypeValue.isUnknown(value)) {
      recordVariableType(var, withNotNull(getDfType(var)));
      return;
    }

    DfType dfType = filterDfTypeOnAssignment(var, getDfType(value)).meet(var.getDfType());
    if (dfType == DfType.BOTTOM) return; // likely uncompilable code or bad CFG
    if (value instanceof DfaVariableValue && !ControlFlow.isTempVariable(var) &&
        !ControlFlow.isTempVariable((DfaVariableValue)value) &&
        (var.getQualifier() == null || !ControlFlow.isTempVariable(var.getQualifier()))) {
      // assigning a = b when b is known to be null: could be ephemeral
      checkEphemeral(var, value);
    }
    recordVariableType(var, dfType);
    applyBinOpRelations(value, RelationType.EQ, var);
    applyRelation(var, value, false);
    Couple<DfaValue> specialFields = getSpecialEquivalencePair(var, value);
    if (specialFields != null && specialFields.getFirst() instanceof DfaVariableValue) {
      setVarValue((DfaVariableValue)specialFields.getFirst(), specialFields.getSecond());
    }
  }

  protected DfType filterDfTypeOnAssignment(DfaVariableValue var, @NotNull DfType dfType) {
    return dfType;
  }

  private DfaValue handleStackValueOnVariableFlush(DfaValue value,
                                                   DfaVariableValue flushed,
                                                   DfaVariableValue replacement) {
    if (value.dependsOn(flushed)) {
      DfType dfType = getDfType(value);
      if (value instanceof DfaVariableValue) {
        if (replacement != null) {
          DfaVariableValue target = replaceQualifier((DfaVariableValue)value, flushed, replacement);
          if (target != value) return target;
        }
      }
      return myFactory.fromDfType(dfType);
    }
    return value;
  }

  private int getOrCreateEqClassIndex(@NotNull DfaVariableValue dfaValue) {
    int i = getEqClassIndex(dfaValue);
    if (i != -1) return i;
    dfaValue = canonicalize(dfaValue);
    EqClass eqClass = new EqClass(myFactory);
    eqClass.add(dfaValue.getID());

    int resultIndex = storeClass(eqClass);
    checkInvariants();

    return resultIndex;
  }

  private int storeClass(EqClass eqClass) {
    int freeIndex = myEqClasses.indexOf(null);
    int resultIndex = freeIndex >= 0 ? freeIndex : myEqClasses.size();
    if (freeIndex >= 0) {
      myEqClasses.set(freeIndex, eqClass);
    }
    else {
      myEqClasses.add(eqClass);
    }
    eqClass.forValues(id -> myIdToEqClassesIndices.put(id, resultIndex));
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
    int[] thisToThat = getClassesMap(that);
    if (thisToThat == null) return false;
    for (DistinctPairSet.DistinctPair pair : myDistinctClasses) {
      int firstIndex = thisToThat[pair.getFirstIndex()];
      int secondIndex = thisToThat[pair.getSecondIndex()];
      if (firstIndex == -1 || secondIndex == -1 || firstIndex == secondIndex) return false;
      RelationType relation = that.myDistinctClasses.getRelation(firstIndex, secondIndex);
      if (relation == null || pair.isOrdered() && relation != RelationType.LT) return false;
    }
    for (Map.Entry<DfaVariableValue, DfType> entry : this.myVariableTypes.entrySet()) {
      DfaVariableValue value = entry.getKey();
      DfType thisType = entry.getValue();
      // the inherent variable type is not always a superstate for any non-inherent type
      // (e.g. inherent can be nullable, but current type can be notnull)
      // so we cannot limit checking to myVariableTypes map only
      DfType thatType = that.getDfType(value);
      if(!thisType.isMergeable(thatType)) return false;
    }
    for (Map.Entry<DfaVariableValue, DfType> entry : that.myVariableTypes.entrySet()) {
      DfaVariableValue value = entry.getKey();
      if (this.myVariableTypes.containsKey(value)) continue; // already processed in the previous loop
      DfType thisType = this.getDfType(value);
      DfType thatType = entry.getValue();
      if(!thisType.isMergeable(thatType)) return false;
    }
    return true;
  }

  /**
   * Returns an int array which maps this state class indices to that state class indices.
   *
   * @param that other state to map class indices
   * @return an int array which values are indices of the corresponding that state class which contains
   * all the values from this state class or -1 if there's no corresponding that state class.
   * Null is returned if at least one of this state classes contains values which do not belong to the same
   * class in that state
   */
  private int @Nullable [] getClassesMap(DfaMemoryStateImpl that) {
    List<EqClass> thisClasses = this.myEqClasses;
    List<EqClass> thatClasses = that.myEqClasses;
    int thisSize = thisClasses.size();
    int thatSize = thatClasses.size();
    int[] thisToThat = new int[thisSize];
    // If any two values are equivalent in this, they also must be equivalent in that
    for (int thisIdx = 0; thisIdx < thisSize; thisIdx++) {
      EqClass thisClass = thisClasses.get(thisIdx);
      thisToThat[thisIdx] = -1;
      if (thisClass != null) {
        boolean found = false;
        for (int thatIdx = 0; thatIdx < thatSize; thatIdx++) {
          EqClass thatClass = thatClasses.get(thatIdx);
          if (thatClass != null && thatClass.containsAll(thisClass)) {
            thisToThat[thisIdx] = thatIdx;
            found = true;
            break;
          }
        }
        if (!found && thisClass.size() > 1) return null;
      }
    }
    return thisToThat;
  }

  @Override
  public boolean shouldCompareByEquals(DfaValue dfaLeft, DfaValue dfaRight) {
    if (dfaLeft == dfaRight && !(dfaLeft instanceof DfaWrappedValue) && !(dfaLeft.getDfType() instanceof DfConstantType)) {
      return false;
    }
    return TypeConstraint.fromDfType(getDfType(dfaLeft)).isComparedByEquals() &&
           TypeConstraint.fromDfType(getDfType(dfaRight)).isComparedByEquals();
  }

  private static boolean isSuperValue(DfaValue superValue, DfaValue subValue) {
    if (DfaTypeValue.isUnknown(superValue) || superValue == subValue) return true;
    if (superValue instanceof DfaTypeValue && subValue instanceof DfaTypeValue) {
      return superValue.getDfType().isMergeable(subValue.getDfType());
    }
    return false;
  }

  public List<EqClass> getEqClasses() {
    return myEqClasses;
  }

  private @Nullable EqClass getEqClass(DfaValue value) {
    int index = getEqClassIndex(value);
    return index == -1 ? null : myEqClasses.get(index);
  }

  /**
   * Returns existing equivalence class index or -1 if not found
   * @param dfaValue value to find a class for
   * @return class index or -1 if not found
   */
  public int getEqClassIndex(@NotNull DfaValue dfaValue) {
    int classIndex = myIdToEqClassesIndices.getOrDefault(dfaValue.getID(), -1);
    if (classIndex == -1) {
      dfaValue = canonicalize(dfaValue);
      classIndex = myIdToEqClassesIndices.getOrDefault(dfaValue.getID(), -1);
    }

    if (classIndex == -1) return -1;

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
  private boolean uniteClasses(DfaVariableValue val1, DfaVariableValue val2) {
    DfaVariableValue var1 = getCanonicalVariable(val1);
    DfaVariableValue var2 = getCanonicalVariable(val2);
    int c1Index = getOrCreateEqClassIndex(val1);
    int c2Index = getOrCreateEqClassIndex(val2);
    if (c1Index == c2Index) return true;

    if (!myDistinctClasses.unite(c1Index, c2Index)) return false;

    EqClass c1 = myEqClasses.get(c1Index);
    EqClass c2 = myEqClasses.get(c2Index);

    EqClass newClass = new EqClass(c1);

    myEqClasses.set(c1Index, newClass);
    for (int i = 0; i < c2.size(); i++) {
      int c = c2.get(i);
      newClass.add(c);
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
    List<DfaVariableValue> vars = new ArrayList<>(myVariableTypes.keySet());
    for (DfaVariableValue var : vars) {
      DfaVariableValue target = replaceQualifier(var, from, to);
      if (target != var) {
        DfType fromType = myVariableTypes.remove(var);
        if (fromType != null) {
          DfType toType = myVariableTypes.get(target);
          if (toType == null) {
            toType = fromType;
          }
          else {
            toType = fromType.meet(toType);
            if (toType == DfType.BOTTOM) return false;
          }
          recordVariableType(target, toType);
        }
      }
    }
    for (int valueId : myIdToEqClassesIndices.keySet().toIntArray()) {
      DfaValue value = myFactory.getValue(valueId);
      DfaVariableValue var = ObjectUtils.tryCast(value, DfaVariableValue.class);
      if (var == null || var.getQualifier() != from) continue;
      DfaVariableValue target = var.withQualifier(to);
      if (!uniteClasses(var, target)) return false;
      removeEquivalence(var);
    }
    return true;
  }

  private void checkInvariants() {
    if (!LOG.isDebugEnabled() && !ApplicationManager.getApplication().isEAP()) return;
    for (Int2IntMap.Entry entry : myIdToEqClassesIndices.int2IntEntrySet()) {
      EqClass eqClass = myEqClasses.get(entry.getIntValue());
      if (eqClass == null || !eqClass.contains(entry.getIntKey())) {
        LOG.error("Invariant violated: null-class for id=" + myFactory.getValue(entry.getIntKey()));
      }
    }
    Int2ObjectMap<BitSet> graph = new Int2ObjectOpenHashMap<>();
    for (DistinctPairSet.DistinctPair pair : myDistinctClasses) {
      if (pair.isOrdered()) {
        BitSet set = graph.get(pair.getFirstIndex());
        if (set == null) {
          set = new BitSet();
          graph.put(pair.getFirstIndex(), set);
        }
        set.set(pair.getSecondIndex());
      }
      pair.check();
    }
    BitSet visited = new BitSet();
    BitSet stack = new BitSet();
    for (int v : graph.keySet()) {
      if (isCycle(v, graph, visited, stack)) {
        throw new IllegalStateException("Cycle in distinct pairs involving " + myEqClasses.get(v));
      }
    }
  }

  private static boolean isCycle(int v, Int2ObjectMap<BitSet> graph, BitSet visited, BitSet stack) {
    if (!visited.get(v)) {
      visited.set(v);
      stack.set(v);
      BitSet set = graph.get(v);
      if (set != null && set.stream().anyMatch(i -> !visited.get(i) && isCycle(i, graph, visited, stack) || stack.get(i))) {
        return true;
      }
    }
    stack.clear(v);
    return false;
  }

  public boolean isNull(DfaValue value) {
    return getDfType(value) == DfTypes.NULL;
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

  private void convertReferenceEqualityToValueEquality(DfaValue value) {
    int id = canonicalize(value).getID();
    int index = myIdToEqClassesIndices.getOrDefault(id, -1);
    if (index == -1) return;
    for (Iterator<DistinctPairSet.DistinctPair> iterator = myDistinctClasses.iterator(); iterator.hasNext(); ) {
      DistinctPairSet.DistinctPair pair = iterator.next();
      EqClass otherClass = pair.getOtherClass(index);
      if (otherClass != null && !isNull(otherClass.getVariable(0))) {
        iterator.remove();
      }
    }
  }

  @Override
  public void setDfType(@NotNull DfaValue value, @NotNull DfType dfType) {
    if (value instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue)value;
      DfType type = getDfType(var);
      if (DfaNullability.fromDfType(type) != DfaNullability.fromDfType(dfType)) {
        removeEquivalence(var);
      }
      recordVariableType(var, dfType);
    }
  }

  @Override
  public boolean meetDfType(@NotNull DfaValue value, @NotNull DfType dfType) {
    if (dfType == DfType.TOP) return true;
    if (dfType == DfType.BOTTOM) return false;
    if (value instanceof DfaBinOpValue) {
      return propagateRangeBack(ObjectUtils.tryCast(dfType, DfIntegralType.class), (DfaBinOpValue)value);
    }
    if (value instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue)value;
      DfType type = getDfType(var);
      DfType result = type.meet(dfType);
      if (result.equals(type)) return true;
      if (result == DfType.BOTTOM) return false;
      recordVariableType(var, result);
      TypeConstraint newConstraint = TypeConstraint.fromDfType(result);
      if (newConstraint.isComparedByEquals() && !newConstraint.equals(TypeConstraint.fromDfType(type))) {
        // Type is narrowed to java.lang.String, java.lang.Integer, etc.: we consider String & boxed types
        // equivalence by content, but other object types by reference, so we need to remove distinct pairs, if any.
        convertReferenceEqualityToValueEquality(value);
      }
      if (!updateDependentVariables(var, result)) return false;
      if (result instanceof DfConstantType) {
        if (!propagateConstant(var, (DfConstantType<?>)result)) return false;
      }
      if (result instanceof DfIntegralType) {
        if (!applyRangeToRelatedValues(var, ((DfIntegralType)result).getRange())) return false;
      }
      return true;
    }
    return value.getDfType().meet(dfType) != DfType.BOTTOM;
  }

  private boolean propagateRangeBack(@Nullable DfIntegralType appliedRange, @NotNull DfaBinOpValue binOp) {
    if (appliedRange == null) return true;
    DfaVariableValue left = binOp.getLeft();
    DfaValue right = binOp.getRight();
    DfIntegralType leftDfType = ObjectUtils.tryCast(getDfType(left), DfIntegralType.class);
    DfIntegralType rightDfType = ObjectUtils.tryCast(getDfType(right), DfIntegralType.class);
    if(leftDfType == null || rightDfType == null) return true;
    DfType result = getBinOpRange(binOp);
    DfType targetRange = result.meet(appliedRange);
    if (targetRange == DfType.BOTTOM) return false;
    DfType leftConstraint = binOp.getDfType();
    DfType rightConstraint = binOp.getDfType();
    switch (binOp.getOperation()) {
      case PLUS:
        leftConstraint = appliedRange.eval(rightDfType, LongRangeBinOp.MINUS);
        rightConstraint = appliedRange.eval(leftDfType, LongRangeBinOp.MINUS);
        break;
      case MINUS:
        leftConstraint = rightDfType.eval(appliedRange, LongRangeBinOp.PLUS);
        rightConstraint = leftDfType.eval(appliedRange, LongRangeBinOp.MINUS);
        break;
      case MOD:
        Long value = rightDfType.getRange().getConstantValue();
        if (value != null) {
          leftConstraint = leftDfType.meetRange(LongRangeSet.fromRemainder(value, DfLongType.extractRange(targetRange)));
        }
        break;
    }
    return meetDfType(left, leftDfType.meet(leftConstraint)) && meetDfType(right, rightDfType.meet(rightConstraint));
  }

  @Override
  public boolean applyContractCondition(@NotNull DfaCondition condition) {
    if (condition instanceof DfaRelation) {
      DfaRelation relation = (DfaRelation)condition;
      if (relation.isEquality()) {
        checkEphemeral(relation.getLeftOperand(), relation.getRightOperand());
        checkEphemeral(relation.getRightOperand(), relation.getLeftOperand());
      }
    }
    return applyCondition(condition);
  }

  @Override
  public boolean areEqual(@NotNull DfaValue value1, @NotNull DfaValue value2) {
    if (value1 instanceof DfaBinOpValue && value2 instanceof DfaBinOpValue) {
      DfaBinOpValue binOp1 = (DfaBinOpValue)value1;
      DfaBinOpValue binOp2 = (DfaBinOpValue)value2;
      return binOp1.getOperation() == binOp2.getOperation() &&
             areEqual(binOp1.getLeft(), binOp2.getLeft()) &&
             areEqual(binOp1.getRight(), binOp2.getRight());
    }
    DfType dfType1 = getDfType(value1);
    DfType dfType2 = getDfType(value2);
    if (dfType1 instanceof DfConstantType && dfType2 instanceof DfConstantType && dfType1.equals(dfType2)) return true;
    if (!(value1 instanceof DfaVariableValue)) return false;
    if (!(value2 instanceof DfaVariableValue)) return false;
    if (value1 == value2) return true;
    int index1 = getEqClassIndex(value1);
    int index2 = getEqClassIndex(value2);
    return index1 != -1 && index1 == index2;
  }

  @Override
  public @Nullable RelationType getRelation(@NotNull DfaValue left, @NotNull DfaValue right) {
    int leftClass = getEqClassIndex(left);
    int rightClass = getEqClassIndex(right);
    if (leftClass == -1 || rightClass == -1) return null;
    if (leftClass == rightClass) return RelationType.EQ;
    return myDistinctClasses.getRelation(leftClass, rightClass);
  }

  @Override
  public boolean applyCondition(@NotNull DfaCondition dfaCond) {
    if (!(dfaCond instanceof DfaRelation)) {
      return dfaCond != DfaCondition.getFalse();
    }
    return applyRelationCondition((DfaRelation)dfaCond);
  }

  private boolean applyRelationCondition(@NotNull DfaRelation dfaRelation) {
    DfaValue dfaLeft = dfaRelation.getLeftOperand();
    DfaValue dfaRight = dfaRelation.getRightOperand();
    RelationType relationType = dfaRelation.getRelation();

    if (DfaTypeValue.isUnknown(dfaLeft) || DfaTypeValue.isUnknown(dfaRight)) return true;
    // Such relations are only useful to update ephemeral marks in applyContractCondition
    if (dfaLeft instanceof DfaTypeValue && dfaRight instanceof DfaTypeValue) return true;

    if (relationType == RelationType.EQ && dfaLeft instanceof DfaVariableValue && dfaRight instanceof DfaVariableValue) {
      checkEphemeral(dfaLeft, dfaRight);
      checkEphemeral(dfaRight, dfaLeft);
    }

    DfType leftType = getDfType(dfaLeft);
    DfType rightType = getDfType(dfaRight);

    if (leftType == DfType.FAIL || rightType == DfType.FAIL) {
      return (leftType == rightType) == (relationType == RelationType.EQ);
    }

    if (!meetDfType(dfaLeft, leftType.meet(rightType.fromRelation(relationType)))) {
      return false;
    }
    if (relationType.getFlipped() != null && !meetDfType(dfaRight, rightType.meet(leftType.fromRelation(relationType.getFlipped())))) {
      return false;
    }

    if (!applyBinOpRelations(dfaLeft, relationType, dfaRight)) return false;

    if (leftType instanceof DfFloatingPointType && rightType instanceof DfFloatingPointType && relationType.getFlipped() != null) {
      RelationType constantRelation = getFloatingConstantRelation(leftType, rightType);
      if (constantRelation != null) {
        return relationType.isSubRelation(constantRelation);
      }
      if (canBeNaN(leftType) || canBeNaN(rightType)) {
        if (dfaLeft == dfaRight && dfaLeft instanceof DfaVariableValue && !(dfaLeft.getDfType() instanceof DfPrimitiveType)) {
          return !dfaRelation.isNonEquality();
        }
        applyEquivalenceRelation(relationType, dfaLeft, dfaRight);
        return true;
      }
    }

    if (dfaRight instanceof DfaTypeValue) {
      if ((relationType == RelationType.EQ || relationType.isInequality()) &&
          !applyUnboxedRelation(dfaLeft, dfaRight, relationType.isInequality())) {
        return false;
      }
    }

    return applyEquivalenceRelation(relationType, dfaLeft, dfaRight);
  }

  private boolean applyBinOpRelations(DfaValue left, RelationType type, DfaValue right) {
    if (type != RelationType.LT && type != RelationType.GT && type != RelationType.NE && type != RelationType.EQ) return true;
    if (!(left instanceof DfaBinOpValue)) {
      if (right instanceof DfaBinOpValue) {
        return applyBinOpRelations(right, type.getFlipped(), left);
      }
      return true;
    }
    DfaBinOpValue binOp = (DfaBinOpValue)left;
    LongRangeBinOp op = binOp.getOperation();
    if (op != LongRangeBinOp.PLUS && op != LongRangeBinOp.MINUS) return true;
    DfaVariableValue leftLeft = binOp.getLeft();
    DfaValue leftRight = binOp.getRight();
    LongRangeSet leftRange = DfLongType.extractRange(getDfType(leftLeft));
    LongRangeSet rightRange = DfLongType.extractRange(getDfType(leftRight));
    boolean isLong = binOp.getDfType() instanceof DfLongType;
    LongRangeSet rightNegated = rightRange.negate(isLong);
    LongRangeSet rightCorrected = op == LongRangeBinOp.MINUS ? rightNegated : rightRange;

    LongRangeSet resultRange = DfLongType.extractRange(getDfType(right));
    RelationType correctedRelation = correctRelation(type, leftRange, rightCorrected, resultRange, isLong);
    if (op == LongRangeBinOp.MINUS) {
      long min = resultRange.min();
      long max = resultRange.max();
      if (min == 0 && max == 0) {
        // a-b (rel) 0 => a (rel) b
        if (!applyCondition(leftLeft.cond(correctedRelation, leftRight))) return false;
      }
      else if (min == 0 && type == RelationType.GT || min >= 1 && RelationType.GE.isSubRelation(type)) {
        RelationType correctedGt = correctRelation(RelationType.GT, leftRange, rightCorrected, resultRange, isLong);
        if (!applyCondition(leftLeft.cond(correctedGt, leftRight))) return false;
      }
      else if (max == 0 && type == RelationType.LT || max <= -1 && RelationType.LE.isSubRelation(type)) {
        RelationType correctedLt = correctRelation(RelationType.LT, leftRange, rightCorrected, resultRange, isLong);
        if (!applyCondition(leftLeft.cond(correctedLt, leftRight))) return false;
      }
      if (RelationType.EQ.equals(type) && !resultRange.contains(0)) {
        // a-b == non-zero => a != b
        if (!applyRelation(leftLeft, leftRight, true)) return false;
      }
    }
    if (op == LongRangeBinOp.PLUS && RelationType.EQ == type &&
        !resultRange.intersects(LongRangeSet.all().mul(LongRangeSet.point(2), true))) {
      // a+b == odd => a != b
      if (!applyRelation(leftLeft, leftRight, true)) return false;
    }
    if (right instanceof DfaVariableValue) {
      // a+b (rel) c && a == c => b (rel) 0
      if (areEqual(leftLeft, right)) {
        RelationType finalRelation = op == LongRangeBinOp.MINUS ?
                                     Objects.requireNonNull(correctedRelation.getFlipped()) : correctedRelation;
        if (!applyCondition(leftRight.cond(finalRelation, binOp.getDfType().meetRange(LongRangeSet.point(0))))) return false;
      }
      // a+b (rel) c && b == c => a (rel) 0
      if (op == LongRangeBinOp.PLUS && areEqual(leftRight, right)) {
        if (!applyCondition(leftLeft.cond(correctedRelation, binOp.getDfType().meetRange(LongRangeSet.point(0))))) return false;
      }

      if (!applyRelationOnAddition(type, leftLeft, leftRange, rightCorrected, right, isLong)) return false;
      if (op == LongRangeBinOp.PLUS && leftRight instanceof DfaVariableValue) {
        if (!applyRelationOnAddition(type, (DfaVariableValue)leftRight, rightRange, leftRange, right, isLong)) return false;
      }
    }
    return true;
  }

  private boolean applyRelationOnAddition(@NotNull RelationType type,
                                          @NotNull DfaVariableValue left,
                                          @NotNull LongRangeSet leftRange,
                                          @NotNull LongRangeSet rightRange,
                                          @NotNull DfaValue sum,
                                          boolean isLong) {
    if (!leftRange.additionMayOverflow(rightRange, isLong)) {
      // a-positiveNumber >= b => a > b
      if (rightRange.max() < 0 && RelationType.GE.isSubRelation(type)) {
        if (!applyLessThanRelation(sum, left)) return false;
      }
      // a+positiveNumber >= b => a > b
      if (rightRange.min() > 0 && RelationType.LE.isSubRelation(type)) {
        if (!applyLessThanRelation(left, sum)) return false;
      }
    }
    if (RelationType.EQ == type && !rightRange.contains(0)) {
      // a+nonZero == b => a != b
      if (!applyRelation(left, sum, true)) return false;
    }
    return true;
  }

  private static RelationType correctRelation(RelationType relation, LongRangeSet summand1, LongRangeSet summand2,
                                              LongRangeSet resultRange, boolean isLong) {
    if (relation != RelationType.LT && relation != RelationType.GT) return relation;
    boolean overflowPossible = true;
    if (!isLong) {
      LongRangeSet overflowRange = getIntegerSumOverflowValues(summand1, summand2);
      overflowPossible = !overflowRange.isEmpty() && (resultRange == null || resultRange.fromRelation(relation).intersects(overflowRange));
    }
    return overflowPossible ? RelationType.NE : relation;
  }

  private static @NotNull LongRangeSet getIntegerSumOverflowValues(LongRangeSet left, LongRangeSet right) {
    if (left.isEmpty() || right.isEmpty()) return LongRangeSet.empty();
    long sumMin = left.min() + right.min();
    long sumMax = left.max() + right.max();
    LongRangeSet result = LongRangeSet.empty();
    if (sumMin < Integer.MIN_VALUE) {
      result = result.unite(LongRangeSet.range((int)sumMin, Integer.MAX_VALUE));
    }
    if (sumMax > Integer.MAX_VALUE) {
      result = result.unite(LongRangeSet.range(Integer.MIN_VALUE, (int)sumMax));
    }
    return result;
  }

  private boolean applyEquivalenceRelation(RelationType type, DfaValue dfaLeft, DfaValue dfaRight) {
    RelationType currentRelation = getRelation(dfaLeft, dfaRight);
    if (currentRelation != null) {
      // Eq: NE & GE => GT
      type = type.meet(currentRelation);
      if (type == null) return false;
    }
    boolean isNegated = type == RelationType.NE || type == RelationType.GT || type == RelationType.LT;
    if (!isNegated && type != RelationType.EQ) {
      return true;
    }

    if (type == RelationType.EQ && !applySpecialFieldEquivalence(dfaLeft, dfaRight)) return false;

    if (dfaLeft instanceof DfaVariableValue && dfaRight instanceof DfaVariableValue && !isNegated) {
      if (!updateQualifierOnEquality((DfaVariableValue)dfaLeft, dfaRight) ||
          !updateQualifierOnEquality((DfaVariableValue)dfaRight, dfaLeft)) {
        return false;
      }
    }

    if (dfaLeft == dfaRight && !(dfaLeft instanceof DfaWrappedValue)) {
      return !isNegated || (dfaLeft instanceof DfaVariableValue && ((DfaVariableValue)dfaLeft).containsCalls());
    }

    if (dfaLeft instanceof DfaVariableValue && dfaRight instanceof DfaVariableValue) {
      if (type == RelationType.LT) {
        if (!applyLessThanRelation(dfaLeft, dfaRight)) return false;
      } else if (type == RelationType.GT) {
        if (!applyLessThanRelation(dfaRight, dfaLeft)) return false;
      } else {
        if (!applyRelation(dfaLeft, dfaRight, isNegated)) return false;
      }
    }
    return applyUnboxedRelation(dfaLeft, dfaRight, isNegated);
  }

  private void checkEphemeral(DfaValue left, DfaValue right) {
    if (getDfType(right) == DfTypes.NULL) {
      DfaNullability nullability = DfaNullability.fromDfType(getDfType(left));
      if (nullability == DfaNullability.UNKNOWN || nullability == DfaNullability.FLUSHED) {
        markEphemeral();
      }
    }
  }

  private boolean updateQualifierOnEquality(DfaVariableValue target, DfaValue value) {
    DfType constraint = target.getDescriptor().getQualifierConstraintFromValue(this, value);
    DfaVariableValue qualifier = target.getQualifier();
    return qualifier == null || meetDfType(qualifier, constraint);
  }

  private boolean propagateConstant(DfaVariableValue value, DfConstantType<?> constant) {
    JvmSpecialField field = JvmSpecialField.fromQualifierType(constant);
    if (field != null) {
      if (!meetDfType(field.createValue(getFactory(), value), field.fromConstant(constant.getValue()))) return false;
    }
    DfType dfType = constant.tryNegate();
    if (dfType == null) return true;
    EqClass eqClass = getEqClass(value);
    if (eqClass == null) return true;
    for (DistinctPairSet.DistinctPair pair : getDistinctClassPairs().toArray(new DistinctPairSet.DistinctPair[0])) {
      EqClass other = pair.getFirst() == eqClass ? pair.getSecond() : pair.getSecond() == eqClass ? pair.getFirst() : null;
      if (other != null) {
        for (DfaVariableValue var : other.asList()) {
          if (!meetDfType(var, dfType)) return false;
        }
      }
    }
    return true;
  }

  private boolean applyRangeToRelatedValues(DfaValue value, LongRangeSet appliedRange) {
    EqClass eqClass = getEqClass(value);
    if (eqClass == null) return true;
    for (DistinctPairSet.DistinctPair pair : getDistinctClassPairs().toArray(new DistinctPairSet.DistinctPair[0])) {
      if (pair.isOrdered()) {
        if (pair.getFirst() == eqClass) {
          if (!applyRelationRangeToClass(pair.getSecond(), appliedRange, RelationType.GT)) return false;
        } else if(pair.getSecond() == eqClass) {
          if (!applyRelationRangeToClass(pair.getFirst(), appliedRange, RelationType.LT)) return false;
        }
      }
    }
    return true;
  }

  private boolean applyRelationRangeToClass(EqClass eqClass, LongRangeSet range, RelationType relationType) {
    LongRangeSet appliedRange = range.fromRelation(relationType);
    for (DfaVariableValue var : eqClass.asList()) {
      DfType rangeType = DfTypes.rangeClamped(appliedRange, var.getDfType() instanceof DfLongType);
      if (!meetDfType(var, rangeType)) return false;
    }
    return true;
  }

  private Couple<DfaValue> getSpecialEquivalencePair(DfaVariableValue left, DfaValue right) {
    if (right instanceof DfaVariableValue) return null;
    SpecialField field = JvmSpecialField.fromQualifier(left);
    if (field == null) return null;
    DfaValue leftValue = field.createValue(myFactory, left);
    DfaValue rightValue = field.createValue(myFactory, right);
    return Couple.of(leftValue, rightValue);
  }

  private boolean applySpecialFieldEquivalence(@NotNull DfaValue left, @NotNull DfaValue right) {
    Couple<DfaValue> pair = left instanceof DfaVariableValue ? getSpecialEquivalencePair((DfaVariableValue)left, right) :
                            right instanceof DfaVariableValue ? getSpecialEquivalencePair((DfaVariableValue)right, left) : null;
    if (pair == null || isNaN(pair.getFirst()) || isNaN(pair.getSecond())) return true;
    return applyCondition(pair.getFirst().eq(pair.getSecond()));
  }

  private boolean applyUnboxedRelation(@NotNull DfaValue dfaLeft, DfaValue dfaRight, boolean negated) {
    TypeConstraint leftConstraint = TypeConstraint.fromDfType(dfaLeft.getDfType());
    TypeConstraint rightConstraint = TypeConstraint.fromDfType(dfaRight.getDfType());
    if (dfaLeft instanceof DfaVariableValue && !leftConstraint.isPrimitiveWrapper() ||
        dfaRight instanceof DfaVariableValue && !rightConstraint.isPrimitiveWrapper()) {
      return true;
    }
    if (leftConstraint.isPrimitiveWrapper() &&
        rightConstraint.isPrimitiveWrapper() && leftConstraint.meet(rightConstraint) == TypeConstraints.BOTTOM) {
      // Boxes of different type (e.g. Long and Integer), cannot be equal even if unboxed values are equal
      return negated;
    }

    DfaValue unboxedLeft = JvmSpecialField.UNBOX.createValue(myFactory, dfaLeft);
    DfaValue unboxedRight = JvmSpecialField.UNBOX.createValue(myFactory, dfaRight);
    DfType leftDfType = getDfType(unboxedLeft);
    DfType rightDfType = getDfType(unboxedRight);
    if (leftDfType instanceof DfConstantType && rightDfType instanceof DfConstantType) {
      return leftDfType.equals(rightDfType) != negated;
    }
    if (negated && leftDfType instanceof DfFloatingPointType) {
      // If floating point wrappers are not equal, unboxed versions could still be equal if they are 0.0 and -0.0
      return true;
    }
    return applyRelation(unboxedLeft, unboxedRight, negated);
  }

  private static boolean isNaN(@NotNull DfaValue dfa) {
    DfType type = dfa.getDfType();
    return type instanceof DfConstantType && DfaUtil.isNaN(((DfConstantType<?>)type).getValue());
  }

  private static boolean canBeNaN(@NotNull DfType dfType) {
    return dfType.isSuperType(DfTypes.floatValue(Float.NaN)) || dfType.isSuperType(DfTypes.doubleValue(Double.NaN));
  }

  private boolean applyRelation(@NotNull DfaValue dfaLeft, @NotNull DfaValue dfaRight, boolean isNegated) {
    if (!(dfaLeft instanceof DfaVariableValue) || !(dfaRight instanceof DfaVariableValue)) return true;
    int c1Index = getOrCreateEqClassIndex((DfaVariableValue)dfaLeft);
    int c2Index = getOrCreateEqClassIndex((DfaVariableValue)dfaRight);
    if (c1Index == c2Index) return !isNegated;

    if (!isNegated) { //Equals
      if (isUnstableValue(dfaLeft) || isUnstableValue(dfaRight)) return true;
      if (!uniteClasses((DfaVariableValue)dfaLeft, (DfaVariableValue)dfaRight)) return false;
    }
    else { // Not Equals
      myDistinctClasses.addUnordered(c1Index, c2Index);
    }
    myCachedNonTrivialEqClasses = null;
    myCachedHash = null;

    return true;
  }

  private boolean applyLessThanRelation(@NotNull DfaValue dfaLeft, @NotNull DfaValue dfaRight) {
    if (!(dfaLeft instanceof DfaVariableValue) || !(dfaRight instanceof DfaVariableValue)) return true;
    int c1Index = getOrCreateEqClassIndex((DfaVariableValue)dfaLeft);
    int c2Index = getOrCreateEqClassIndex((DfaVariableValue)dfaRight);
    if (c1Index == c2Index) return false;

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
    return !var.alwaysEqualsToItself() && !(getDfType(var) instanceof DfConstantType);
  }

  private static @Nullable RelationType getFloatingConstantRelation(DfType leftType, DfType rightType) {
    Number value1 = leftType.getConstantOfType(Number.class);
    Number value2 = rightType.getConstantOfType(Number.class);
    if (value1 == null || value2 == null) return null;
    double double1 = value1.doubleValue();
    double double2 = value2.doubleValue();
    if (double1 == 0.0 && double2 == 0.0) return RelationType.EQ;
    int cmp = Double.compare(double1, double2);
    return cmp == 0 ? RelationType.EQ : cmp < 0 ? RelationType.LT : RelationType.GT;
  }

  public @NotNull DfType getBinOpRange(DfaBinOpValue binOp) {
    DfIntegralType leftType = ObjectUtils.tryCast(getDfType(binOp.getLeft()), DfIntegralType.class);
    DfIntegralType rightType = ObjectUtils.tryCast(getDfType(binOp.getRight()), DfIntegralType.class);
    if (leftType == null || rightType == null) return binOp.getDfType();
    LongRangeSet left = leftType.getRange();
    LongRangeSet right = rightType.getRange();
    LongRangeBinOp op = binOp.getOperation();
    DfIntegralType result = ObjectUtils.tryCast(leftType.eval(rightType, op), DfIntegralType.class);
    if (result == null) {
      result = binOp.getDfType();
    }
    boolean isLong = result instanceof DfLongType;
    if (op == LongRangeBinOp.MINUS) {
      RelationType rel = getRelation(binOp.getLeft(), binOp.getRight());
      if (rel == RelationType.NE) {
        return result.meetRange(LongRangeSet.all().without(0));
      }
      if (!left.subtractionMayOverflow(right, isLong)) {
        if (rel == RelationType.GT) {
          return result.meetRange(LongRangeSet.range(1, Long.MAX_VALUE));
        }
        if (rel == RelationType.LT) {
          return result.meetRange(LongRangeSet.range(Long.MIN_VALUE, -1));
        }
      }
    }
    if (op == LongRangeBinOp.PLUS && areEqual(binOp.getLeft(), binOp.getRight())) {
      return leftType.eval(binOp.getDfType().meetRange(LongRangeSet.point(2)), LongRangeBinOp.MUL);
    }
    return result;
  }

  @Override
  public @NotNull DfType getUnboxedDfType(@NotNull DfaValue value) {
    if (value instanceof DfaWrappedValue && ((DfaWrappedValue)value).getSpecialField() == JvmSpecialField.UNBOX) {
      return getDfType(((DfaWrappedValue)value).getWrappedValue());
    }
    if (value instanceof DfaVariableValue && TypeConstraint.fromDfType(value.getDfType()).isPrimitiveWrapper()) {
      return getDfType(JvmSpecialField.UNBOX.createValue(myFactory, value));
    }
    if (value instanceof DfaTypeValue) {
      DfReferenceType refType = ObjectUtils.tryCast(value.getDfType(), DfReferenceType.class);
      if (refType != null && refType.getSpecialField() == JvmSpecialField.UNBOX) {
        return refType.getSpecialFieldType();
      }
    }
    return getDfType(value);
  }

  @Override
  public @NotNull DfType getDfType(@NotNull DfaValue value) {
    if (value instanceof DfaBinOpValue) {
      return getBinOpRange((DfaBinOpValue)value);
    }
    if (value instanceof DfaVariableValue) {
      DfType type = getRecordedType((DfaVariableValue)value);
      return type != null ? type : ((DfaVariableValue)value).getInherentType();
    }
    return value.getDfType();
  }

  void recordVariableType(@NotNull DfaVariableValue dfaVar, @NotNull DfType type) {
    dfaVar = canonicalize(dfaVar);
    if (type instanceof DfReferenceType) {
      type = ((DfReferenceType)type).dropSpecialField();
    }
    if (type.equals(dfaVar.getInherentType())) {
      myVariableTypes.remove(dfaVar);
    } else {
      myVariableTypes.put(dfaVar, type);
    }
    if (type instanceof DfEphemeralType) {
      markEphemeral();
    }
    myCachedHash = null;
  }

  private boolean updateDependentVariables(DfaVariableValue dfaVar, DfType type) {
    if (!updateQualifierOnEquality(dfaVar, dfaVar)) return false;
    EqClass eqClass = getEqClass(dfaVar);
    if (eqClass != null) {
      type = type.fromRelation(RelationType.EQ);
      for (DfaVariableValue value : eqClass.asList()) {
        if (value != dfaVar) {
          recordVariableType(value, type);
          if (!updateQualifierOnEquality(value, value)) return false;
        }
      }
    }
    return true;
  }

  private @NotNull DfaValue canonicalize(@NotNull DfaValue value) {
    if (value instanceof DfaVariableValue) {
      return canonicalize((DfaVariableValue)value);
    }
    if (value instanceof DfaWrappedValue) {
      DfaWrappedValue boxedValue = (DfaWrappedValue)value;
      DfaValue canonicalized = canonicalize(boxedValue.getWrappedValue());
      if (canonicalized == boxedValue.getWrappedValue()) return boxedValue;
      return myFactory.getWrapperFactory().createWrapper(boxedValue.getDfType(), boxedValue.getSpecialField(), canonicalized);
    }
    return value;
  }

  private @NotNull DfaVariableValue canonicalize(DfaVariableValue var) {
    DfaVariableValue qualifier = var.getQualifier();
    if (qualifier != null) {
      int index = myIdToEqClassesIndices.getOrDefault(qualifier.getID(), -1);
      if (index == -1) {
        qualifier = canonicalize(qualifier);
        index = myIdToEqClassesIndices.getOrDefault(qualifier.getID(), -1);
        if (index == -1) {
          return var.withQualifier(qualifier);
        }
      }

      return var.withQualifier(Objects.requireNonNull(myEqClasses.get(index).getCanonicalVariable()));
    }
    return var;
  }

  private DfType getRecordedType(DfaVariableValue var) {
    DfType type = myVariableTypes.get(var);
    if (type != null) {
      return type;
    }
    DfaVariableValue canonicalized = canonicalize(var);
    return canonicalized == var ? null : myVariableTypes.get(canonicalized);
  }

  public void forRecordedVariableTypes(BiConsumer<? super DfaVariableValue, ? super DfType> consumer) {
    myVariableTypes.forEach(consumer);
  }

  @Override
  public void flushFieldsQualifiedBy(@NotNull Set<DfaValue> qualifiers) {
    flushFields(new QualifierStatusMap(qualifiers));
  }

  @Override
  public void flushFields() {
    flushFields(new QualifierStatusMap(null));
  }

  public void flushFields(@NotNull DfaMemoryStateImpl.QualifierStatusMap qualifierStatusMap) {
    Set<DfaVariableValue> vars = new LinkedHashSet<>();
    for (DfaVariableValue value : myVariableTypes.keySet()) {
      if (value.isFlushableByCalls() && qualifierStatusMap.shouldFlush(value.getQualifier(), value.containsCalls())) {
        vars.add(value);
      }
    }
    for (EqClass aClass : myEqClasses) {
      if (aClass != null) {
        for (DfaVariableValue value : aClass) {
          if (value.isFlushableByCalls() && qualifierStatusMap.shouldFlush(value.getQualifier(), value.containsCalls())) {
            vars.add(value);
          }
        }
      }
    }
    for (DfaVariableValue value : vars) {
      doFlush(value, true);
    }
    myStack.replaceAll(val -> {
      DfType type = val.getDfType();
      if (type instanceof DfReferenceType) {
        SpecialField field = ((DfReferenceType)type).getSpecialField();
        if (field != null && !field.isStable() && qualifierStatusMap.shouldFlush(val, field.isCall())) {
          return myFactory.fromDfType(((DfReferenceType)type).dropSpecialField());
        }
      }
      return val;
    });
  }

  @Override
  public void flushVariable(@NotNull DfaVariableValue variable) {
    flushVariable(variable, true);
  }

  @Override
  public void flushVariable(@NotNull DfaVariableValue variable, boolean canonicalize) {
    DfaVariableValue canonical = canonicalize ? canonicalize(variable) : variable;
    EqClass eqClass = canonical.getDependentVariables().isEmpty() ? null : getEqClass(canonical);
    DfaVariableValue newCanonical =
      eqClass == null ? null : StreamEx.of(eqClass.iterator()).without(canonical).min(EqClass.CANONICAL_VARIABLE_COMPARATOR)
        .filter(candidate -> !candidate.dependsOn(canonical))
        .orElse(null);
    myStack.replaceAll(value -> handleStackValueOnVariableFlush(value, canonical, newCanonical));

    doFlush(canonical, false);
    flushDependencies(canonical);
    myCachedHash = null;
  }

  void flushDependencies(@NotNull DfaVariableValue variable) {
    for (DfaVariableValue dependent : variable.getDependentVariables().toArray(new DfaVariableValue[0])) {
      doFlush(dependent, false);
    }
  }

  private void flushQualifiedMethods(@NotNull DfaVariableValue variable) {
    DfaVariableValue qualifier = variable.getQualifier();
    if (qualifier != null) {
      // Flush method results on field write
      List<DfaVariableValue> toFlush =
        ContainerUtil.filter(qualifier.getDependentVariables(), DfaVariableValue::containsCalls);
      toFlush.forEach(val -> doFlush(val, true));
    }
  }

  void doFlush(@NotNull DfaVariableValue var, boolean markFlushed) {
    DfType typeBefore = getDfType(var);
    if(isNull(var)) {
      myStack.replaceAll(val -> val == var ? myFactory.fromDfType(DfTypes.NULL) : val);
    }

    removeEquivalence(var);
    myVariableTypes.remove(var);
    if (markFlushed) {
      DfType inherentType = var.getInherentType();
      DfType correctedType = inherentType.correctTypeOnFlush(typeBefore);
      if (!inherentType.equals(correctedType)) {
        recordVariableType(var, correctedType);
      }
    }
    myCachedHash = null;
  }

  void removeEquivalence(DfaVariableValue var) {
    int varID = var.getID();
    int varClassIndex = myIdToEqClassesIndices.getOrDefault(varID, -1);
    if (varClassIndex == -1) {
      var = canonicalize(var);
      varID = var.getID();
      varClassIndex = myIdToEqClassesIndices.getOrDefault(varID, -1);
      if (varClassIndex == -1) return;
    }

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
    else {
      DfaVariableValue newCanonical = varClass.getCanonicalVariable();
      if (newCanonical != null && previousCanonical != null && previousCanonical != newCanonical &&
          (ControlFlow.isTempVariable(previousCanonical) && !newCanonical.dependsOn(previousCanonical) ||
           newCanonical.getDepth() <= previousCanonical.getDepth())) {
        // Do not transfer to deeper qualifier. E.g. if we have two classes like (a, b.c) (a.d, e),
        // and flushing `a`, we do not convert `a.d` to `b.c.d`. Otherwise infinite qualifier explosion is possible.
        boolean successfullyConverted = convertQualifiers(previousCanonical, newCanonical);
        assert successfullyConverted;
      }
    }

    myCachedNonTrivialEqClasses = null;
    myCachedHash = null;
  }

  /**
   * @return a mergeability key. If two states return the same key, then states could be merged via {@link #merge(DfaMemoryStateImpl)}.
   */
  Object getMergeabilityKey() {
    /*
      States are mergeable if:
      - Ephemeral flag is the same
      - Stack depth is the same
      - All DfaControlTransferValues in the stack are the same (otherwise finally blocks may not complete successfully)
      - Top-of-stack value is the same (otherwise we may prematurely merge true/false on TOS right before jump which is very undesired)
     */
    return StreamEx.of(myStack).<Object>mapLastOrElse(val -> ObjectUtils.tryCast(val, DfaControlTransferValue.class),
                                                      Function.identity())
      .append(isEphemeral()).toImmutableList();
  }

  /**
   * Updates this DfaMemoryState so that it becomes a minimal superstate which covers the other state as well
   *
   * @param other other state which has equal {@link #getMergeabilityKey()}
   */
  void merge(DfaMemoryStateImpl other) {
    assert other.isEphemeral() == isEphemeral();
    assert other.myStack.size() == myStack.size();
    ProgressManager.checkCanceled();
    retainEquivalences(other);
    mergeDistinctPairs(other);
    mergeVariableTypes(other);
    mergeStacks(other);
    myCachedHash = null;
    myCachedNonTrivialEqClasses = null;
    afterMerge(other);
  }

  /**
   * Custom logic to be implemented by subclasses
   * @param other other memory start this one was merged with
   */
  protected void afterMerge(DfaMemoryStateImpl other) {

  }

  private void mergeStacks(DfaMemoryStateImpl other) {
    List<DfaValue> values = StreamEx.zip(myStack, other.myStack, DfaValue::unite).toList();
    myStack.clear();
    values.forEach(myStack::push);
  }

  private void mergeDistinctPairs(DfaMemoryStateImpl other) {
    ArrayList<DistinctPairSet.DistinctPair> pairs = new ArrayList<>(myDistinctClasses);
    for (DistinctPairSet.DistinctPair pair : pairs) {
      EqClass first = pair.getFirst();
      EqClass second = pair.getSecond();
      RelationType relation = other.getRelation(myFactory.getValue(first.get(0)), myFactory.getValue(second.get(0)));
      if (relation == null || relation == RelationType.EQ) {
        myDistinctClasses.remove(pair);
      }
      else if (pair.isOrdered() && relation != RelationType.LT) {
        myDistinctClasses.dropOrder(pair);
      }
    }
  }

  private void mergeVariableTypes(DfaMemoryStateImpl other) {
    Set<DfaVariableValue> vars = StreamEx.of(myVariableTypes, other.myVariableTypes).toFlatCollection(Map::keySet, HashSet::new);
    for (DfaVariableValue var : vars) {
      DfType type = getDfType(var);
      DfType otherType = other.getDfType(var);
      DfType result;
      if (!type.equals(otherType)) {
        result = type.join(otherType).correctTypeOnFlush(type).correctTypeOnFlush(otherType);
      } else {
        result = type;
      }
      recordVariableType(var, result);
    }
  }

  private void retainEquivalences(DfaMemoryStateImpl other) {
    boolean needRestart = true;
    while (needRestart) {
      ProgressManager.checkCanceled();
      needRestart = false;
      for (EqClass eqClass : new ArrayList<>(myEqClasses)) {
        if (eqClass != null && retainEquivalences(eqClass, other)) {
          needRestart = true;
          break;
        }
      }
    }
  }

  /**
   * Retain only those equivalences from given class which are present in other memory state
   *
   * @param eqClass an equivalence class to process
   * @param other   other memory state. If it does not contain all the equivalences from the eqClass, the eqClass will
   *                be split to retain only remaining equivalences
   * @return true if not only given class, but also some other classes were updated due to canonicalization
   */
  private boolean retainEquivalences(EqClass eqClass, DfaMemoryStateImpl other) {
    if (eqClass.size() <= 1) return false;
    List<EqClass> groups = splitEqClass(eqClass, other);
    if (groups.size() == 1) return false;

    IntList addedClasses = new IntArrayList();
    int origIndex = myIdToEqClassesIndices.get(eqClass.get(0));
    for (EqClass group : groups) {
      addedClasses.add(storeClass(group));
    }
    int[] addedClassesArray = addedClasses.toIntArray();
    myDistinctClasses.splitClass(origIndex, addedClassesArray);
    myEqClasses.set(origIndex, null);

    DfaVariableValue from = eqClass.getCanonicalVariable();
    boolean otherClassChanged = false;
    if (from != null && !from.getDependentVariables().isEmpty()) {
      List<DfaVariableValue> vars = new ArrayList<>(myVariableTypes.keySet());
      for (int classIndex : addedClassesArray) {
        DfaVariableValue to = myEqClasses.get(classIndex).getCanonicalVariable();
        if (to == null || to == from || to.getDepth() > from.getDepth()) continue;

        for (DfaVariableValue var : vars) {
          DfaVariableValue target = replaceQualifier(var, from, to);
          if (target != var) {
            recordVariableType(target, getDfType(var));
          }
        }
        for (int valueId : myIdToEqClassesIndices.keySet().toIntArray()) {
          DfaValue value = myFactory.getValue(valueId);
          DfaVariableValue var = ObjectUtils.tryCast(value, DfaVariableValue.class);
          if (var == null || var.getQualifier() != from) continue;
          DfaVariableValue target = var.withQualifier(to);
          boolean united = uniteClasses(var, target);
          assert united;
          otherClassChanged = true;
        }
      }
    }
    checkInvariants();
    return otherClassChanged;
  }

  /**
   * Splits given EqClass to several classes removing equivalences absent in other state
   *
   * @param eqClass an equivalence class to split
   * @param other   other memory state; only equivalences present in that state should be preserved
   * @return list of created classes (the original class remains unchanged). Trivial classes are also included,
   * thus sum of resulting class sizes is equal to the original class size
   */
  private @NotNull List<EqClass> splitEqClass(EqClass eqClass, DfaMemoryStateImpl other) {
    Int2ObjectMap<EqClass> groupsInClasses = new Int2ObjectOpenHashMap<>();
    List<EqClass> groups = new ArrayList<>();
    for (DfaVariableValue value : eqClass.asList()) {
      int otherClass = other.getEqClassIndex(value);
      EqClass list;
      if (otherClass == -1) {
        list = new EqClass(myFactory);
        groups.add(list);
      }
      else {
        list = groupsInClasses.get(otherClass);
        if (list == null) {
          list = new EqClass(myFactory);
          groupsInClasses.put(otherClass, list);
        }
      }
      list.add(value.getID());
    }
    groups.addAll(groupsInClasses.values());
    return groups;
  }

  private static DfType withNotNull(DfType type) {
    if (type instanceof DfReferenceType) {
      return ((DfReferenceType)type).getNullability() == DfaNullability.NOT_NULL ? type :
             ((DfReferenceType)type).dropNullability();
    }
    return type;
  }

  enum QualifierStatus {
    SHOULD_FLUSH_ALWAYS, SHOULD_FLUSH_CALLS, SHOULD_NOT_FLUSH
  }

  private final class QualifierStatusMap {
    private final Int2ObjectMap<QualifierStatus> myMap = new Int2ObjectOpenHashMap<>();
    private final @Nullable Set<DfaValue> myQualifiersToFlush;

    private QualifierStatusMap(@Nullable Set<DfaValue> qualifiersToFlush) {
      myQualifiersToFlush = qualifiersToFlush;
    }

    boolean shouldFlush(@Nullable DfaValue qualifier, boolean hasCall) {
      if (qualifier == null) return true;
      QualifierStatus status = myMap.get(qualifier.getID());
      if (status == null) {
        status = calculate(qualifier);
        if (status != QualifierStatus.SHOULD_FLUSH_ALWAYS && qualifier instanceof DfaVariableValue) {
          DfaVariableValue qualifierVar = (DfaVariableValue)qualifier;
          if (qualifierVar.isFlushableByCalls()) {
            DfaVariableValue qualifierQualifier = qualifierVar.getQualifier();
            if (shouldFlush(qualifierQualifier, qualifierVar.containsCalls())) {
              status = QualifierStatus.SHOULD_FLUSH_ALWAYS;
            }
          }
        }
        myMap.put(qualifier.getID(), status);
      }
      return status == QualifierStatus.SHOULD_FLUSH_ALWAYS || (hasCall && status == QualifierStatus.SHOULD_FLUSH_CALLS);
    }

    private @NotNull QualifierStatus calculate(@NotNull DfaValue qualifier) {
      final DfReferenceType dfType = ObjectUtils.tryCast(getDfType(qualifier), DfReferenceType.class);
      if (dfType == null) return QualifierStatus.SHOULD_FLUSH_ALWAYS;
      if (dfType.getMutability() == Mutability.UNMODIFIABLE) return QualifierStatus.SHOULD_NOT_FLUSH;
      if (dfType.isLocal()) {
        return myQualifiersToFlush != null && myQualifiersToFlush.contains(qualifier) ?
               QualifierStatus.SHOULD_FLUSH_ALWAYS : QualifierStatus.SHOULD_NOT_FLUSH;
      }
      if (myQualifiersToFlush == null) return QualifierStatus.SHOULD_FLUSH_ALWAYS;
      boolean flushCalls = false;
      for (final DfaValue qualifierToFlush : myQualifiersToFlush) {
        final RelationType relation = getRelation(qualifier, qualifierToFlush);
        if (relation == RelationType.EQ) return QualifierStatus.SHOULD_FLUSH_ALWAYS;
        final DfType typeToFlush = getDfType(qualifierToFlush);
        if (typeToFlush instanceof DfReferenceType && ((DfReferenceType)typeToFlush).isLocal()) {
          continue;
        }
        // Calls may refer to changed object indirectly (unless it's local)
        flushCalls = true;
        if (relation != null) continue;
        if (typeToFlush.meet(dfType) != DfType.BOTTOM) {
          // possible aliasing
          return QualifierStatus.SHOULD_FLUSH_ALWAYS;
        }
      }
      return flushCalls ? QualifierStatus.SHOULD_FLUSH_CALLS : QualifierStatus.SHOULD_NOT_FLUSH;
    }
  }

  @Override
  public void widen() {
    myVariableTypes.replaceAll((var, type) -> type.widen());
  }
}
