// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.memory;

import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeType;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfEphemeralType;
import com.intellij.codeInspection.dataFlow.types.DfIntegralType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import it.unimi.dsi.fastutil.ints.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

/**
 * Invariant: qualifiers of the variables used in myEqClasses or myVariableTypes must be canonical variables
 * where canonical variable is the minimal DfaVariableValue inside its eqClass, according to EqClass#CANONICAL_VARIABLE_COMPARATOR.
 */
public class DfaMemoryStateImpl implements DfaMemoryState {
  private static final Logger LOG = Logger.getInstance(DfaMemoryStateImpl.class);

  private final @NotNull DfaValueFactory myFactory;

  private final List<EqClass> myEqClasses;
  // dfa value id -> indices in myEqClasses list of the classes which contain the id
  protected final Int2IntMap myIdToEqClassesIndices;
  protected final Stack<DfaValue> myStack;
  private final DistinctPairSet myDistinctClasses;
  private final LinkedHashMap<DfaVariableValue,DfType> myVariableTypes;
  private boolean myEphemeral;

  public DfaMemoryStateImpl(@NotNull DfaValueFactory factory) {
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
    copy.flushFields(new QualifierStatusMap(null, true));
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

  protected DistinctPairSet getDistinctClassPairs() {
    return myDistinctClasses;
  }

  private LinkedHashSet<EqClass> myCachedNonTrivialEqClasses;
  protected LinkedHashSet<EqClass> getNonTrivialEqClasses() {
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
    flushVariable(var, var.getDfType().isMergeable(var.getInherentType()));
    flushQualifiedMethods(var);

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
    if (!(value instanceof DfaVariableValue)) {
      for (VariableDescriptor desc : dfType.getDerivedVariables()) {
        DfaValue derivedVar = desc.createValue(getFactory(), var);
        DfaValue derivedValue = desc.createValue(getFactory(), value);
        if (derivedVar instanceof DfaVariableValue) {
          setVarValue((DfaVariableValue)derivedVar, derivedValue);
        }
      }
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

  @Override
  public @Nullable DfaMemoryState tryJoinExactly(@NotNull DfaMemoryState other) {
    DfaMemoryStateImpl that = (DfaMemoryStateImpl)other;
    StateMerger merger = new StateMerger(this, that);
    if (!merger.update(that.myEphemeral || !myEphemeral, myEphemeral || !that.myEphemeral)) return null;
    if (myStack.size() != that.myStack.size()) return null;
    for (int i = 0; i < myStack.size(); i++) {
      DfaValue thisValue = myStack.get(i);
      DfaValue thatValue = that.myStack.get(i);
      int finalI = i;
      if (!merger.update(isSuperValue(thisValue, thatValue),
                           isSuperValue(thatValue, thisValue),
                           () -> {
                             if (thisValue instanceof DfaTypeValue && thatValue instanceof DfaTypeValue) {
                               DfType type = thisValue.getDfType().tryJoinExactly(thatValue.getDfType());
                               if (type != null) {
                                 return new MergePatch(false, ms -> ms.myStack.set(finalI, myFactory.fromDfType(type)));
                               }
                             }
                             return null;
                           })) {
        return null;
      }
    }
    int[] thisToThat = getClassesMap(that);
    if (!merger.update(thisToThat != null, true)) return null;
    int[] thatToThis = that.getClassesMap(this);
    if (!merger.update(true, thatToThis != null)) return null;
    if (thisToThat != null) {
      for (DistinctPairSet.DistinctPair pair : myDistinctClasses) {
        int firstIndex = thisToThat[pair.getFirstIndex()];
        int secondIndex = thisToThat[pair.getSecondIndex()];
        if (!merger.updateEquivalence(pair, firstIndex, secondIndex, false)) return null;
        RelationType relation = that.myDistinctClasses.getRelation(firstIndex, secondIndex);
        if (!merger.updateOrdering(pair, relation, false)) return null;
      }
    }
    if (thatToThis != null) {
      for (DistinctPairSet.DistinctPair pair : that.myDistinctClasses) {
        int firstIndex = thatToThis[pair.getFirstIndex()];
        int secondIndex = thatToThis[pair.getSecondIndex()];
        if (!merger.updateEquivalence(pair, firstIndex, secondIndex, true)) return null;
        RelationType relation = myDistinctClasses.getRelation(firstIndex, secondIndex);
        if (!merger.updateOrdering(pair, relation, true)) return null;
      }
    }
    for (Map.Entry<DfaVariableValue, DfType> entry : this.myVariableTypes.entrySet()) {
      DfaVariableValue value = entry.getKey();
      DfType thisType = entry.getValue();
      // the inherent variable type is not always a superstate for any non-inherent type
      // (e.g. inherent can be nullable, but current type can be notnull)
      // so we cannot limit checking to myVariableTypes map only
      DfType thatType = that.getDfType(value);
      if (!merger.updateVariable(value, thisType, thatType)) return null;
    }
    for (Map.Entry<DfaVariableValue, DfType> entry : that.myVariableTypes.entrySet()) {
      DfaVariableValue value = entry.getKey();
      if (this.myVariableTypes.containsKey(value)) continue; // already processed in the previous loop
      DfType thisType = this.getDfType(value);
      DfType thatType = entry.getValue();
      if (!merger.updateVariable(value, thisType, thatType)) return null;
    }
    return merger.merge(this, that);
  }

  private static class MergePatch {
    final boolean myApplyToRight;
    final Consumer<DfaMemoryStateImpl> myPatcher;

    private MergePatch(boolean right, Consumer<DfaMemoryStateImpl> patcher) {
      myApplyToRight = right;
      myPatcher = patcher;
    }

    DfaMemoryStateImpl apply(DfaMemoryStateImpl left, DfaMemoryStateImpl right) {
      DfaMemoryStateImpl result = (myApplyToRight ? right : left).createCopy();
      myPatcher.accept(result);
      result.afterMerge(myApplyToRight ? left : right);
      return result;
    }
  }
  
  private static class UpdateVariableMergePatch extends MergePatch {
    private final DfaVariableValue myValue;
    private final DfType myType;

    private UpdateVariableMergePatch(DfaVariableValue value, DfType type) {
      super(false, s -> s.recordVariableType(value, type));
      myValue = value;
      myType = type;
    }
  }
  
  private static class DropOrderingMergePatch extends MergePatch {
    private final DistinctPairSet.DistinctPair myPair;

    private DropOrderingMergePatch(boolean right, DistinctPairSet.DistinctPair pair) {
      this(right, pair, ms -> ms.myDistinctClasses.dropOrder(pair));
    }

    private DropOrderingMergePatch(boolean right, DistinctPairSet.DistinctPair pair, Consumer<DfaMemoryStateImpl> diff) {
      super(right, diff);
      myPair = pair;
    }
  }

  // Two memory states can be merged exactly if a.isSuperState(b) (then return a), b.isSuperState(a) (then return b),
  // or they have mergeable difference in exactly one variable. This class tracks which of these cases are possible.
  private static class StateMerger {
    private final DfaMemoryStateImpl myLeftState;
    private final DfaMemoryStateImpl myRightState;
    private boolean myMaybeThisSuper = true, myMaybeThatSuper = true;
    private @Nullable MergePatch mySingleDiff = null;

    private StateMerger(DfaMemoryStateImpl left, DfaMemoryStateImpl right) {
      myLeftState = left;
      myRightState = right;
    }

    boolean update(boolean thisSuper, boolean thatSuper, Supplier<MergePatch> singleDiff) {
      if (thisSuper && thatSuper) return true;
      MergePatch diff = null;
      if (myMaybeThatSuper && myMaybeThisSuper) {
        assert mySingleDiff == null;
        diff = singleDiff.get();
      }
      else if (mySingleDiff != null) {
        diff = tryMergeDiffs(singleDiff);
      }
      myMaybeThisSuper &= thisSuper;
      myMaybeThatSuper &= thatSuper;
      if (!myMaybeThisSuper && !myMaybeThatSuper && diff == null) return false;
      mySingleDiff = diff;
      return true;
    }

    @Nullable
    private MergePatch tryMergeDiffs(Supplier<MergePatch> singleDiff) {
      if (!(mySingleDiff instanceof DropOrderingMergePatch)) return null;
      MergePatch diff = singleDiff.get();
      if (diff instanceof DropOrderingMergePatch && diff.myApplyToRight != mySingleDiff.myApplyToRight) {
        DistinctPairSet.DistinctPair oldPair = ((DropOrderingMergePatch)mySingleDiff).myPair;
        DistinctPairSet.DistinctPair newPair = ((DropOrderingMergePatch)diff).myPair;
        if (oldPair.getFirst().equals(newPair.getSecond()) && newPair.getFirst().equals(oldPair.getSecond())) {
          // The same pair but found starting from the other state
          return mySingleDiff;
        }
      }
      // ordering like a < b may affect types of the variables: in this case merge both types and ordering
      if (diff instanceof UpdateVariableMergePatch) {
        UpdateVariableMergePatch updateVar = (UpdateVariableMergePatch)diff;
        DfaVariableValue var = updateVar.myValue;
        DfaVariableValue otherVar;
        DistinctPairSet.DistinctPair pair = ((DropOrderingMergePatch)mySingleDiff).myPair;
        boolean left;
        if (pair.getFirst().contains(var.getID())) {
          left = true;
          otherVar = pair.getSecond().getCanonicalVariable();
        }
        else if (pair.getSecond().contains(var.getID())) {
          left = false;
          otherVar = pair.getFirst().getCanonicalVariable();
        } else {
          return null;
        }
        if (otherVar == null) return null;
        if (mySingleDiff.myApplyToRight) {
          left = !left;
        }
        if (updateVar.myType.meetRelation(left ? RelationType.LT : RelationType.GT, myLeftState.getDfType(otherVar))
              .equals(myLeftState.getDfType(var)) &&
            updateVar.myType.meetRelation(left ? RelationType.GT : RelationType.LT, myRightState.getDfType(otherVar))
              .equals(myRightState.getDfType(var))) {
          return new DropOrderingMergePatch(mySingleDiff.myApplyToRight, pair, mySingleDiff.myPatcher.andThen(diff.myPatcher));
        }
      }
      return null;
    }

    boolean update(boolean thisSuper, boolean thatSuper) {
      return update(thisSuper, thatSuper, () -> null);
    }

    boolean updateVariable(DfaVariableValue value, DfType thisType, DfType thatType) {
      return update(thisType.isMergeable(thatType), thatType.isMergeable(thisType),
                    () -> {
                      DfType type = thisType.tryJoinExactly(thatType);
                      if (type != null) {
                        return new UpdateVariableMergePatch(value, type);
                      }
                      return null;
                    });
    }

    boolean updateEquivalence(DistinctPairSet.DistinctPair pair, int firstIndex, int secondIndex, boolean rightDistinct) {
      if (firstIndex != -1 && secondIndex != -1 && firstIndex != secondIndex) return true;
      return update(rightDistinct, !rightDistinct, () -> {
        if (firstIndex == secondIndex && !pair.isOrdered()) {
          DfaVariableValue canonicalVariable = pair.getFirst().getCanonicalVariable();
          if (canonicalVariable != null) {
            return new MergePatch(!rightDistinct, ms -> ms.removeEquivalence(canonicalVariable));
          }
        }
        return null;
      });
    }

    boolean updateOrdering(DistinctPairSet.DistinctPair pair, RelationType relation, boolean rightDistinct) {
      if (relation != null && (!pair.isOrdered() || relation == RelationType.LT)) return true;
      return update(rightDistinct, !rightDistinct, () -> {
        if (pair.isOrdered() && relation == RelationType.GT) {
          return new DropOrderingMergePatch(rightDistinct, pair);
        }
        return null;
      });
    }

    DfaMemoryStateImpl merge(DfaMemoryStateImpl left, DfaMemoryStateImpl right) {
      if (myMaybeThisSuper) return left;
      if (myMaybeThatSuper) return right;
      assert mySingleDiff != null;
      return mySingleDiff.apply(left, right);
    }
  }

  @Override
  public boolean isSuperStateOf(@NotNull DfaMemoryState other) {
    if (!(other instanceof DfaMemoryStateImpl)) return false;
    DfaMemoryStateImpl that = (DfaMemoryStateImpl)other;
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
  public void setDfType(@NotNull DfaValue value, @NotNull DfType dfType) {
    if (value instanceof DfaVariableValue) {
      recordVariableType((DfaVariableValue)value, dfType);
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
      return meetVariableType(var, type, result);
    }
    return value.getDfType().meet(dfType) != DfType.BOTTOM;
  }

  protected boolean meetVariableType(@NotNull DfaVariableValue var, @NotNull DfType originalType, @NotNull DfType newType) {
    recordVariableType(var, newType);
    for (DerivedVariableDescriptor desc : newType.getDerivedVariables()) {
      if (!meetDfType(desc.createValue(getFactory(), var), newType.getDerivedValue(desc))) {
        return false;
      }
    }
    if (!updateDependentVariables(var, newType)) return false;
    if (!correctRelatedValues(var, newType)) return false;
    if (newType instanceof DfConstantType && !propagateConstant(var, (DfConstantType<?>)newType)) return false;
    return true;
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
          leftConstraint = leftDfType.meetRange(LongRangeSet.fromRemainder(value, extractRange(targetRange)));
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

    if (!meetDfType(dfaLeft, leftType.meetRelation(relationType, rightType))) {
      return false;
    }
    if (relationType.getFlipped() != null && !meetDfType(dfaRight, rightType.meetRelation(relationType.getFlipped(), leftType))) {
      return false;
    }

    if (!applyBinOpRelations(dfaLeft, relationType, dfaRight)) return false;

    return applyEquivalenceRelation(relationType, dfaLeft, dfaRight);
  }

  private static @NotNull LongRangeSet extractRange(@NotNull DfType type) {
    return type instanceof DfIntegralType ? ((DfIntegralType)type).getRange() : LongRangeSet.all();
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
    LongRangeSet leftRange = extractRange(getDfType(leftLeft));
    LongRangeSet rightRange = extractRange(getDfType(leftRight));
    LongRangeType lrType = binOp.getDfType().getLongRangeType();
    LongRangeSet rightNegated = rightRange.negate(lrType);
    LongRangeSet rightCorrected = op == LongRangeBinOp.MINUS ? rightNegated : rightRange;

    LongRangeSet resultRange = extractRange(getDfType(right));
    RelationType correctedRelation = correctRelation(type, leftRange, rightCorrected, resultRange, lrType);
    if (op == LongRangeBinOp.MINUS) {
      long min = resultRange.min();
      long max = resultRange.max();
      if (min == 0 && max == 0) {
        // a-b (rel) 0 => a (rel) b
        if (!applyCondition(leftLeft.cond(correctedRelation, leftRight))) return false;
      }
      else if (min == 0 && type == RelationType.GT || min >= 1 && RelationType.GE.isSubRelation(type)) {
        RelationType correctedGt = correctRelation(RelationType.GT, leftRange, rightCorrected, resultRange, lrType);
        if (!applyCondition(leftLeft.cond(correctedGt, leftRight))) return false;
      }
      else if (max == 0 && type == RelationType.LT || max <= -1 && RelationType.LE.isSubRelation(type)) {
        RelationType correctedLt = correctRelation(RelationType.LT, leftRange, rightCorrected, resultRange, lrType);
        if (!applyCondition(leftLeft.cond(correctedLt, leftRight))) return false;
      }
      if (RelationType.EQ.equals(type) && !resultRange.contains(0)) {
        // a-b == non-zero => a != b
        if (!applyRelation(leftLeft, leftRight, true)) return false;
      }
    }
    if (op == LongRangeBinOp.PLUS && RelationType.EQ == type &&
        !resultRange.intersects(lrType.fullRange().mul(LongRangeSet.point(2), lrType))) {
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

      if (!applyRelationOnAddition(type, leftLeft, leftRange, rightCorrected, right, lrType)) return false;
      if (op == LongRangeBinOp.PLUS && leftRight instanceof DfaVariableValue) {
        if (!applyRelationOnAddition(type, (DfaVariableValue)leftRight, rightRange, leftRange, right, lrType)) return false;
      }
    }
    return true;
  }

  private boolean applyRelationOnAddition(@NotNull RelationType type,
                                          @NotNull DfaVariableValue left,
                                          @NotNull LongRangeSet leftRange,
                                          @NotNull LongRangeSet rightRange,
                                          @NotNull DfaValue sum,
                                          @NotNull LongRangeType lrType) {
    if (!leftRange.additionMayOverflow(rightRange, lrType)) {
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
                                              LongRangeSet resultRange, LongRangeType lrType) {
    if (relation != RelationType.LT && relation != RelationType.GT) return relation;
    boolean overflowPossible = true;
    if (lrType.bytes() < 8) {
      LongRangeSet overflowRange = getSumOverflowValues(summand1, summand2, lrType);
      overflowPossible = !overflowRange.isEmpty() && (resultRange == null || resultRange.fromRelation(relation).intersects(overflowRange));
    }
    return overflowPossible ? RelationType.NE : relation;
  }

  private static @NotNull LongRangeSet getSumOverflowValues(LongRangeSet left,
                                                            LongRangeSet right,
                                                            LongRangeType lrType) {
    if (left.isEmpty() || right.isEmpty()) return LongRangeSet.empty();
    long sumMin = left.min() + right.min();
    long sumMax = left.max() + right.max();
    LongRangeSet result = LongRangeSet.empty();
    if (sumMin < lrType.min()) {
      result = result.join(LongRangeSet.range(lrType.cast(sumMin), lrType.max()));
    }
    if (sumMax > lrType.max()) {
      result = result.join(LongRangeSet.range(lrType.min(), lrType.cast(sumMax)));
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

    if (type == RelationType.EQ && !applyDerivedVariablesEquivalence(dfaLeft, dfaRight)) return false;

    if (dfaLeft instanceof DfaVariableValue && dfaRight instanceof DfaVariableValue && !isNegated) {
      if (!updateQualifierOnEquality((DfaVariableValue)dfaLeft, dfaRight) ||
          !updateQualifierOnEquality((DfaVariableValue)dfaRight, dfaLeft)) {
        return false;
      }
    }

    if (dfaLeft == dfaRight && dfaLeft instanceof DfaVariableValue) {
      return !isNegated || isUnstableValue(dfaLeft) || ((DfaVariableValue)dfaLeft).containsCalls();
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
    if (isNegated) {
      return applyDerivedInequality(dfaLeft, dfaRight);
    }
    return true;
  }

  protected void checkEphemeral(DfaValue left, DfaValue right) {
  }

  private boolean updateQualifierOnEquality(DfaVariableValue target, DfaValue value) {
    DfType constraint = target.getDescriptor().getQualifierConstraintFromValue(this, value);
    DfaVariableValue qualifier = target.getQualifier();
    return qualifier == null || meetDfType(qualifier, constraint);
  }

  private boolean propagateConstant(DfaVariableValue value, DfConstantType<?> constant) {
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

  private boolean correctRelatedValues(@NotNull DfaValue value, @NotNull DfType type) {
    EqClass eqClass = getEqClass(value);
    if (eqClass == null) return true;
    if (type.fromRelation(RelationType.GT) == DfType.TOP && type.fromRelation(RelationType.LT) == DfType.TOP) return true;
    for (DistinctPairSet.DistinctPair pair : getDistinctClassPairs().toArray(new DistinctPairSet.DistinctPair[0])) {
      if (pair.isOrdered()) {
        if (pair.getFirst() == eqClass) {
          DfaVariableValue var = Objects.requireNonNull(pair.getSecond().getCanonicalVariable());
          if (!meetDfType(var, getDfType(var).meetRelation(RelationType.GT, type))) return false;
        } else if(pair.getSecond() == eqClass) {
          DfaVariableValue var = Objects.requireNonNull(pair.getFirst().getCanonicalVariable());
          if (!meetDfType(var, getDfType(var).meetRelation(RelationType.LT, type))) return false;
        }
      }
    }
    return true;
  }

  private boolean applyDerivedVariablesEquivalence(@NotNull DfaValue left, @NotNull DfaValue right) {
    return StreamEx.of(left, right).flatCollection(val -> val.getDfType().getDerivedVariables())
      .allMatch(field -> {
        DfaValue leftValue = field.createValue(myFactory, left);
        DfaValue rightValue = field.createValue(myFactory, right);
        DfType result = getDfType(leftValue).meet(getDfType(rightValue));
        if (!result.hasNonStandardEquivalence() && !applyRelation(leftValue, rightValue, false)) {
          return false;
        }
        return meetDfType(leftValue, result) && meetDfType(rightValue, result);
      });
  }

  private boolean applyDerivedInequality(@NotNull DfaValue dfaLeft, DfaValue dfaRight) {
    if (getDfType(dfaLeft).meet(getDfType(dfaRight)) == DfType.BOTTOM) return true;
    List<DerivedVariableDescriptor> variables = new ArrayList<>(dfaLeft.getDfType().getDerivedVariables());
    variables.retainAll(dfaRight.getDfType().getDerivedVariables());
    return StreamEx.of(variables)
      .filter(dv -> dv.equalityImpliesQualifierEquality())
      .allMatch(dv -> {
        DfaValue derivedLeft = dv.createValue(myFactory, dfaLeft);
        DfaValue derivedRight = dv.createValue(myFactory, dfaRight);
        DfType leftType = getDfType(derivedLeft);
        DfType rightType = getDfType(derivedRight);
        if (leftType instanceof DfConstantType && leftType.equals(rightType)) return false;
        return derivedLeft.getDfType().hasNonStandardEquivalence() ||
               derivedRight.getDfType().hasNonStandardEquivalence() ||
               applyRelation(derivedLeft, derivedRight, true);
      });
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
    return !var.alwaysEqualsToItself(getDfType(var));
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
    if (op == LongRangeBinOp.MINUS) {
      RelationType rel = getRelation(binOp.getLeft(), binOp.getRight());
      if (rel == RelationType.NE) {
        return result.meetRange(LongRangeSet.all().without(0));
      }
      if (!left.subtractionMayOverflow(right, result.getLongRangeType())) {
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
  public @NotNull DfType getDfType(@NotNull DfaValue value) {
    if (value instanceof DfaBinOpValue) {
      return getBinOpRange((DfaBinOpValue)value);
    }
    if (value instanceof DfaVariableValue) {
      DfType type = getRecordedType((DfaVariableValue)value);
      return type != null ? type : ((DfaVariableValue)value).getInherentType();
    }
    if (value instanceof DfaWrappedValue) {
      DfType wrappedValueType = getDfType(((DfaWrappedValue)value).getWrappedValue());
      return ((DfaWrappedValue)value).getSpecialField().asDfType(value.getDfType(), wrappedValueType);
    }
    return value.getDfType();
  }

  public void recordVariableType(@NotNull DfaVariableValue dfaVar, @NotNull DfType type) {
    dfaVar = canonicalize(dfaVar);
    type = type.getBasicType();
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

  @NotNull
  protected DfaValue canonicalize(@NotNull DfaValue value) {
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
    flushFields(new QualifierStatusMap(qualifiers, false));
  }

  @Override
  public void flushFields() {
    flushFields(new QualifierStatusMap(null, false));
  }

  public void flushFields(@NotNull DfaMemoryStateImpl.QualifierStatusMap qualifierStatusMap) {
    Set<DfaVariableValue> vars = new LinkedHashSet<>();
    for (DfaVariableValue value : myVariableTypes.keySet()) {
      if (qualifierStatusMap.shouldFlush(value)) {
        vars.add(value);
      }
    }
    for (EqClass aClass : myEqClasses) {
      if (aClass != null) {
        for (DfaVariableValue value : aClass) {
          if (qualifierStatusMap.shouldFlush(value)) {
            vars.add(value);
          }
        }
      }
    }
    for (DfaVariableValue value : vars) {
      doFlush(value, true);
    }
    myStack.replaceAll(val -> {
      DfType type = getDfType(val);
      if (ContainerUtil.or(type.getDerivedVariables(), dv -> type.getDerivedValue(dv) != DfType.TOP &&
                                                             !dv.isStable() && qualifierStatusMap.shouldFlush(val, dv.isCall()))) {
        return myFactory.fromDfType(type.getBasicType());
      }
      if (val instanceof DfaVariableValue && qualifierStatusMap.shouldFlush((DfaVariableValue)val)) {
        return myFactory.fromDfType(type);
      }
      return val;
    });
  }

  @Override
  public void flushVariable(@NotNull DfaVariableValue variable) {
    flushVariable(variable, true);
  }

  @Override
  public void flushVariables(@NotNull Predicate<@NotNull DfaVariableValue> filter) {
    Set<DfaVariableValue> vars = new HashSet<>();
    for (EqClass aClass : myEqClasses) {
      if (aClass != null) {
        for (DfaVariableValue value : aClass) {
          vars.add(value);
        }
      }
    }
    vars.addAll(myVariableTypes.keySet());
    vars.removeIf(filter.negate());
    vars.forEach(this::flushVariable);
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
    if (variable.isFlushableByCalls()) {
      // Flush method results on field write
      List<DfaVariableValue> toFlush =
        ContainerUtil.filter(myVariableTypes.keySet(), DfaVariableValue::containsCalls);
      toFlush.forEach(val -> doFlush(val, true));
    }
  }

  protected void doFlush(@NotNull DfaVariableValue var, boolean markFlushed) {
    DfType typeBefore = getDfType(var);

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

  @Override
  public Object getMergeabilityKey() {
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

  @Override
  public void merge(@NotNull DfaMemoryState that) {
    DfaMemoryStateImpl other = (DfaMemoryStateImpl)that;
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

  @Override
  public void afterMerge(@NotNull DfaMemoryState other) {

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

  enum QualifierStatus {
    SHOULD_FLUSH_ALWAYS, SHOULD_FLUSH_CALLS, SHOULD_NOT_FLUSH
  }

  private final class QualifierStatusMap {
    private final Int2ObjectMap<QualifierStatus> myMap = new Int2ObjectOpenHashMap<>();
    private final @Nullable Set<DfaValue> myQualifiersToFlush;
    private final boolean myClosure;

    private QualifierStatusMap(@Nullable Set<DfaValue> qualifiersToFlush, boolean closure) {
      myQualifiersToFlush = qualifiersToFlush;
      myClosure = closure;
    }

    boolean shouldFlush(DfaVariableValue value) {
      return (myClosure ? value.canBeCapturedInClosure() : value.isFlushableByCalls()) &&
             shouldFlush(value.getQualifier(), value.containsCalls());
    }

    boolean shouldFlush(@Nullable DfaValue qualifier, boolean hasCall) {
      if (qualifier == null) return true;
      QualifierStatus status = myMap.get(qualifier.getID());
      if (status == null) {
        status = calculate(qualifier);
        if (status != QualifierStatus.SHOULD_FLUSH_ALWAYS && qualifier instanceof DfaVariableValue) {
          DfaVariableValue qualifierVar = (DfaVariableValue)qualifier;
          if (myClosure ? qualifierVar.canBeCapturedInClosure() : qualifierVar.isFlushableByCalls()) {
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
      final DfType dfType = getDfType(qualifier);
      if (dfType.isImmutableQualifier()) return QualifierStatus.SHOULD_NOT_FLUSH;
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
        if (typeToFlush.isLocal()) continue;
        // Calls may refer to changed object indirectly (unless it's local)
        flushCalls = true;
        if (relation != null) continue;
        if (dfType.mayAlias(typeToFlush)) return QualifierStatus.SHOULD_FLUSH_ALWAYS;
      }
      return flushCalls ? QualifierStatus.SHOULD_FLUSH_CALLS : QualifierStatus.SHOULD_NOT_FLUSH;
    }
  }

  @Override
  public void widen() {
    myVariableTypes.replaceAll((var, type) -> type.widen());
  }
}
