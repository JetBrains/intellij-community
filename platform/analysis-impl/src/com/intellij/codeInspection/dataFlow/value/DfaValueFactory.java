// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue.TransferTarget;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue.Trap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.FList;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DfaValueFactory {
  private final @NotNull List<DfaValue> myValues = new ArrayList<>();
  private final @NotNull List<Synthetic> mySynthetics = new ArrayList<>();
  private final @NotNull Project myProject;
  private @Nullable PsiElement myContext;

  /**
   * @param project a project in which context the analysis is performed
   */
  public DfaValueFactory(@NotNull Project project) {
    myProject = project;
    myValues.add(null);
    myVarFactory = new DfaVariableValue.Factory(this);
    myBoxedFactory = new DfaWrappedValue.Factory(this);
    myBinOpFactory = new DfaBinOpValue.Factory(this);
    myTypeValueFactory = new DfaTypeValue.Factory(this);
  }

  /**
   * @return context (can be used to determine variable initial values)
   */
  public @Nullable PsiElement getContext() {
    return myContext;
  }

  /**
   * Sets the analysis context used to determine variable initial values.
   * Must be set before interpretation start by {@link com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter}.
   * Has no meaning during flow building.
   * 
   * @param context current analysis context
   */
  public void setContext(@NotNull PsiElement context) {
    myContext = context;
    for (DfaValue value : myValues) {
      if (value instanceof DfaVariableValue) {
        ((DfaVariableValue)value).resetInherentType();
      }
    }
  }

  int registerValue(DfaValue value) {
    myValues.add(value);
    return myValues.size() - 1;
  }

  public DfaValue getValue(int id) {
    return myValues.get(id);
  }

  /**
   * @param offset offset to search temporary variables from
   * @return temporary variables created at or after the given offset
   */
  public @NotNull List<VariableDescriptor> getTempVariableDescriptorsFrom(int offset) {
    List<VariableDescriptor> result = new ArrayList<>();
    for (Synthetic synthetic : mySynthetics) {
      if (synthetic.myLocation >= offset) {
        result.add(synthetic);
      }
    }
    return result;
  }

  /**
   * Creates temporary variable
   * @param offset offset to create a temporary variable at
   * @param type type of the a temporary variable
   * @return created temporary variable
   */
  public @NotNull DfaVariableValue createTempVariable(int offset, @NotNull DfType type) {
    Synthetic descriptor = new Synthetic(offset, type);
    mySynthetics.add(descriptor);
    return myVarFactory.createVariableValue(descriptor);
  }

  /**
   * @param var variable to check
   * @return true if it's a temp variable
   */
  public static boolean isTempVariable(DfaVariableValue var) {
    return var.getDescriptor() instanceof Synthetic;
  }

  public @NotNull DfaTypeValue getUnknown() {
    return fromDfType(DfType.TOP);
  }

  /**
   * @return a special sentinel value that never equals to anything else (even unknown value) and
   * sometimes pushed on the stack as control flow implementation detail.
   * It's never assigned to the variable or merged with any other value.
   */
  public @NotNull DfaValue getSentinel() {
    return mySentinelValue;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @NotNull DfaTypeValue fromDfType(@NotNull DfType dfType) {
    return myTypeValueFactory.create(dfType);
  }

  public Collection<DfaValue> getValues() {
    return Collections.unmodifiableCollection(myValues);
  }

  public @NotNull DfaControlTransferValue controlTransfer(TransferTarget kind, FList<Trap> traps) {
    return myControlTransfers.get(Pair.create(kind, traps));
  }

  private final Map<Pair<TransferTarget, FList<Trap>>, DfaControlTransferValue> myControlTransfers =
    FactoryMap.create(p -> new DfaControlTransferValue(this, p.first, p.second));

  private final DfaVariableValue.Factory myVarFactory;
  private final DfaWrappedValue.Factory myBoxedFactory;
  private final DfaBinOpValue.Factory myBinOpFactory;
  private final DfaTypeValue.Factory myTypeValueFactory;
  private final DfaValue mySentinelValue = new DfaValue(this) {
    @Override
    public DfaValue bindToFactory(@NotNull DfaValueFactory factory) {
      return factory.mySentinelValue;
    }

    @Override
    public String toString() {
      return "SENTINEL";
    }
  };

  public @NotNull DfaVariableValue.Factory getVarFactory() {
    return myVarFactory;
  }

  public @NotNull DfaWrappedValue.Factory getWrapperFactory() {
    return myBoxedFactory;
  }

  public @NotNull DfaBinOpValue.Factory getBinOpFactory() {
    return myBinOpFactory;
  }

  private static final class Synthetic implements VariableDescriptor {
    private final int myLocation;
    private final DfType myType;

    private Synthetic(int location, DfType type) {
      myLocation = location;
      myType = type;
    }

    @Override
    public @NotNull String toString() {
      return "tmp$" + myLocation;
    }

    @Override
    public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
      return myType;
    }

    @Override
    public boolean isStable() {
      return true;
    }
  }
}
