// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a value that could be operated by data-flow analysis.
 * Values are unique (interned) within the same {@link DfaValueFactory}.
 */
public abstract class DfaValue {
  public static final DfaValue[] EMPTY_ARRAY = new DfaValue[0];
  private final int myID;
  protected final @NotNull DfaValueFactory myFactory;

  protected DfaValue(final @NotNull DfaValueFactory factory) {
    myFactory = factory;
    myID = factory.registerValue(this);
  }

  public @NotNull DfaValueFactory getFactory() {
    return myFactory;
  }

  /**
   * @param factory target factory
   * @return equivalent value registered in the supplied factory
   */
  public abstract DfaValue bindToFactory(@NotNull DfaValueFactory factory);

  public int getID() {
    return myID;
  }

  /**
   * @return a DfType this value belongs under any possible memory state
   */
  public @NotNull DfType getDfType() {
    return DfType.TOP;
  }

  /**
   * Produces a value which describes a union of this value and other value
   *
   * @param other other value to unite with
   * @return a union value. Any particular runtime value which satisfies this value or other value, satisfies also the returned value.
   */
  public DfaValue unite(DfaValue other) {
    if (this == other) return this;
    return myFactory.fromDfType(getDfType().join(other.getDfType()));
  }

  /**
   * Creates an equivalence condition (suitable to pass into {@link DfaMemoryState#applyCondition(DfaCondition)})
   * between this and other value.
   *
   * @param other other value that is tested to be equal to this
   * @return a condition
   */
  public final DfaCondition eq(DfaValue other) {
    return this.cond(RelationType.EQ, other);
  }

  /**
   * Creates an equivalence condition (suitable to pass into {@link DfaMemoryState#applyCondition(DfaCondition)})
   * between this and other value.
   *
   * @param other other value that is tested to be equal to this
   * @return a condition
   */
  public final DfaCondition eq(DfType other) {
    return this.cond(RelationType.EQ, myFactory.fromDfType(other));
  }

  /**
   * Create condition (suitable to pass into {@link DfaMemoryState#applyCondition(DfaCondition)}),
   * evaluating it statically if possible.
   *
   * @param relationType relation
   * @param other        other condition operand
   * @return resulting condition between this value and other operand
   */
  public final @NotNull DfaCondition cond(@NotNull RelationType relationType, @NotNull DfaValue other) {
    return DfaCondition.createCondition(this, relationType, other);
  }

  /**
   * Create condition (suitable to pass into {@link DfaMemoryState#applyCondition(DfaCondition)}),
   * evaluating it statically if possible.
   *
   * @param relationType relation
   * @param other        other condition operand
   * @return resulting condition between this value and other operand
   */
  public @NotNull DfaCondition cond(@NotNull RelationType relationType, @NotNull DfType other) {
    DfaCondition.Exact result = DfaCondition.tryEvaluate(getDfType(), relationType, other);
    if (result != null) return result;
    return DfaCondition.createCondition(this, relationType, myFactory.fromDfType(other));
  }

  public boolean dependsOn(DfaVariableValue other) {
    return false;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof DfaValue && getID() == ((DfaValue)obj).getID();
  }

  @Override
  public int hashCode() {
    return getID();
  }
}
