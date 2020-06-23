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

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaCondition;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface DfaMemoryState {
  @NotNull
  DfaMemoryState createCopy();

  @NotNull
  DfaMemoryState createClosureState();

  /**
   * Pops single value from the top of the stack and returns it
   * @return popped value
   * @throws java.util.EmptyStackException if stack is empty
   */
  @NotNull DfaValue pop();

  /**
   * Reads a value from the top of the stack without popping it
   * @return top of stack value
   * @throws java.util.EmptyStackException if stack is empty
   */
  @NotNull DfaValue peek();

  /**
   * Reads a value from the stack at given offset from the top without popping it
   * @param offset value from the stack (0 = top of stack, 1 = the next one, etc.)
   * @return stack value; null if stack does not deep enough
   * @throws IndexOutOfBoundsException if offset is negative
   */
  @Nullable DfaValue getStackValue(int offset);

  /**
   * Pushes given value to the stack
   * @param value to push
   */
  void push(@NotNull DfaValue value);

  void emptyStack();

  void setVarValue(DfaVariableValue var, DfaValue value);

  /**
   * Returns a relation between given values within this state, if known
   * @param left first value
   * @param right second value
   * @return a relation (EQ, NE, GT, LT), or null if not known.
   */
  @Nullable
  RelationType getRelation(DfaValue left, DfaValue right);

  boolean applyCondition(DfaCondition dfaCond);

  /**
   * Returns true if given two values are known to be equal
   *
   * @param value1 first value to check
   * @param value2 second value to check
   * @return true if they are equal; false if not equal or not known
   */
  boolean areEqual(@NotNull DfaValue value1, @NotNull DfaValue value2);

  boolean applyContractCondition(DfaCondition dfaCond);

  /**
   * Updates value dfType if it's compatible with current value state. 
   * Depending on value passed and memory state implementation the new fact may or may not be memoized.
   * 
   * @param value value to update
   * @param dfType wanted type
   * @return true if update was successful. If false was returned the memory state may be in inconsistent state.
   */
  boolean meetDfType(@NotNull DfaValue value, @NotNull DfType dfType);

  /**
   * Forcibly sets the supplied dfType to given value if given value state can be memoized. 
   * This is necessary to override some knowledge about the variable state. In most of the cases 
   * {@link #meetDfType(DfaValue, DfType)} should be used as it narrows existing type.
   * 
   * @param value value to update.
   * @param dfType type to assign to value. Note that type might be adjusted, e.g. to be compatible with value declared PsiType.
   */
  void setDfType(@NotNull DfaValue value, @NotNull DfType dfType);
  
  /**
   * @param value value to get the type of
   * @return the DfType of the value within this memory state
   */
  @NotNull
  DfType getDfType(@NotNull DfaValue value);

  /**
   * @param value value to get the type of; if value is a primitive wrapper, it will be unboxed before fetching the DfType
   * @return the DfType of the value within this memory state
   */
  @NotNull
  DfType getUnboxedDfType(@NotNull DfaValue value);

  /**
   * @param value value to get the type of
   * @return the PsiType of given value, could be more precise than the declared type. May return null if not known.
   */
  @Nullable
  PsiType getPsiType(@NotNull DfaValue value);

  void flushFieldsQualifiedBy(@NotNull Set<DfaValue> qualifiers);

  void flushFields();

  void flushVariable(@NotNull DfaVariableValue variable);

  boolean isNull(DfaValue dfaVar);

  boolean checkNotNullable(@NotNull DfaValue value);

  boolean isNotNull(DfaValue dfaVar);

  /**
   * Ephemeral means a state that was created when considering a method contract and checking if one of its arguments is null.
   * With explicit null check, that would result in any non-annotated variable being treated as nullable and producing possible NPE warnings later.
   * With contracts, we don't want this. So the state where this variable is null is marked ephemeral and no NPE warnings are issued for such states. 
   */
  void markEphemeral();
  
  boolean isEphemeral();

  boolean isEmptyStack();

  /**
   * Returns true if two given values should be compared by content, rather than by reference.
   * @param dfaLeft left value
   * @param dfaRight right value
   * @return true if two given values should be compared by content, rather than by reference.
   */
  boolean shouldCompareByEquals(DfaValue dfaLeft, DfaValue dfaRight);
}
