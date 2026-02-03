// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class DfaVariableValue extends DfaValue {

  public static class Factory {
    private final Map<Pair<VariableDescriptor, DfaVariableValue>, DfaVariableValue> myExistingVars = new HashMap<>();
    private final DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public @NotNull DfaVariableValue createVariableValue(@NotNull VariableDescriptor descriptor) {
      return createVariableValue(descriptor, null);
    }

    public @NotNull DfaVariableValue createVariableValue(@NotNull VariableDescriptor descriptor, @Nullable DfaVariableValue qualifier) {
      return createVariableValue(descriptor, qualifier, VariableDescriptor::getDfType);
    }

    /**
     * @param descriptor variable descriptor
     * @param qualifier qualifier (if present)
     * @return existing variable value with given descriptor and qualifier; null if it doesn't exist yet
     */
    public @Nullable DfaVariableValue getVariableValue(@NotNull VariableDescriptor descriptor, @Nullable DfaVariableValue qualifier) {
      Pair<VariableDescriptor, DfaVariableValue> key = Pair.create(descriptor, qualifier);
      return myExistingVars.get(key);
    }

    @NotNull
    DfaVariableValue createVariableValue(
      @NotNull VariableDescriptor descriptor,
      @Nullable DfaVariableValue qualifier,
      @NotNull BiFunction<? super @NotNull VariableDescriptor, ? super @Nullable DfaVariableValue, ? extends @NotNull DfType> typeSupplier) {
      Pair<VariableDescriptor, DfaVariableValue> key = Pair.create(descriptor, qualifier);
      DfaVariableValue var = myExistingVars.get(key);
      if (var == null) {
        var = new DfaVariableValue(descriptor, myFactory, qualifier, typeSupplier.apply(descriptor, qualifier));
        myExistingVars.put(key, var);
        while (qualifier != null) {
          qualifier.myDependents.add(var);
          qualifier = qualifier.getQualifier();
        }
      }
      return var;
    }
  }

  private final @NotNull VariableDescriptor myDescriptor;
  private final @NotNull DfType myDfType;
  private final @Nullable DfaVariableValue myQualifier;
  private DfType myInherentType;
  private final List<DfaVariableValue> myDependents = new SmartList<>();

  private DfaVariableValue(@NotNull VariableDescriptor descriptor,
                           @NotNull DfaValueFactory factory,
                           @Nullable DfaVariableValue qualifier,
                           @NotNull DfType type) {
    super(factory);
    myDescriptor = descriptor;
    myQualifier = qualifier;
    myDfType = type;
  }

  public @Nullable PsiElement getPsiVariable() {
    return myDescriptor.getPsiElement();
  }

  public @NotNull VariableDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Override
  public DfaVariableValue bindToFactory(@NotNull DfaValueFactory factory) {
    return factory.getVarFactory().createVariableValue(myDescriptor, myQualifier == null ? null : myQualifier.bindToFactory(factory),
                                                       (descriptor, value) -> myDfType);
  }

  @Override
  public @NotNull DfType getDfType() {
    return myDfType;
  }

  @Override
  public boolean dependsOn(DfaVariableValue other) {
    return other == this || (myQualifier != null && myQualifier.dependsOn(other));
  }

  /**
   * @return list of all variables created within the same factory which are directly or indirectly qualified by this variable.
   */
  public @NotNull List<DfaVariableValue> getDependentVariables() {
    return myDependents;
  }

  public int getDepth() {
    int depth = 0;
    DfaVariableValue qualifier = getQualifier();
    while (qualifier != null) {
      depth++;
      qualifier = qualifier.getQualifier();
    }
    return depth;
  }

  @Contract(pure = true)
  public @NotNull DfaVariableValue withQualifier(DfaVariableValue newQualifier) {
    return newQualifier == myQualifier ? this : myFactory.getVarFactory().createVariableValue(myDescriptor, newQualifier);
  }

  @Override
  public String toString() {
    return (myQualifier == null ? "" : myQualifier + ".") + myDescriptor;
  }

  public @Nullable DfaVariableValue getQualifier() {
    return myQualifier;
  }

  public DfType getInherentType() {
    if(myInherentType == null) {
      myInherentType = myDescriptor.getInitialDfType(this, getFactory().getContext());
    }
    return myInherentType;
  }
  
  void resetInherentType() {
    myInherentType = null;
  }

  public boolean isFlushableByCalls() {
    return !myDescriptor.isStable() || (myQualifier != null && myQualifier.isFlushableByCalls());
  }

  /**
   * @return true if variable can be captured in closure
   */
  public boolean canBeCapturedInClosure() {
    return !myDescriptor.canBeCapturedInClosure() || (myQualifier != null && myQualifier.canBeCapturedInClosure());
  }

  public boolean containsCalls() {
    return myDescriptor.isCall() || myQualifier != null && myQualifier.containsCalls();
  }

  /**
   * @return false if the variable may be not equal to itself when applying the {@link RelationType#EQ}.
   * This could be possible if variable is backed by a pure method that always returns a new object. 
   * @param type current variable type (memory state-specific)
   */
  public boolean alwaysEqualsToItself(@NotNull DfType type) {
    return myDescriptor.alwaysEqualsToItself(type);
  }
}
