// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.TransferTarget;
import com.intellij.codeInspection.dataFlow.Trap;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.FList;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DfaValueFactory {
  private final @NotNull List<DfaValue> myValues = new ArrayList<>();
  private final @NotNull Project myProject;
  private final @Nullable PsiElement myContext;

  /**
   * @param project a project in which context the analysis is performed
   * @param context an item to analyze (code-block, expression, class)
   */
  public DfaValueFactory(@NotNull Project project, @Nullable PsiElement context) {
    myProject = project;
    myContext = context;
    myValues.add(null);
    myVarFactory = new DfaVariableValue.Factory(this);
    myBoxedFactory = new DfaWrappedValue.Factory(this);
    myBinOpFactory = new DfaBinOpValue.Factory(this);
    myTypeValueFactory = new DfaTypeValue.Factory(this);
  }

  public @Nullable PsiElement getContext() {
    return myContext;
  }

  int registerValue(DfaValue value) {
    myValues.add(value);
    return myValues.size() - 1;
  }

  public DfaValue getValue(int id) {
    return myValues.get(id);
  }

  @NotNull
  public DfaTypeValue getInt(int value) {
    return fromDfType(DfTypes.intValue(value));
  }

  @NotNull
  public DfaTypeValue getUnknown() {
    return fromDfType(DfType.TOP);
  }

  /**
   * @return a special sentinel value that never equals to anything else (even unknown value) and
   * sometimes pushed on the stack as control flow implementation detail.
   * It's never assigned to the variable or merged with any other value.
   */
  @NotNull
  public DfaValue getSentinel() {
    return mySentinelValue;
  }

  /**
   * Creates a constant of given type and given value. Constants are always unique
   * (two different constants are not equal to each other).
   *
   * The following types of the objects are supported:
   * <ul>
   *   <li>Integer/Long/Double/Float/Boolean (will be unboxed)</li>
   *   <li>Character/Byte/Short (will be unboxed and widened to int)</li>
   *   <li>String</li>
   *   <li>{@link PsiEnumConstant} (enum constant value, type must be the corresponding enum type)</li>
   *   <li>{@link PsiField} (a final field that contains a unique value, type must be a type of that field)</li>
   *   <li>{@link PsiType} (java.lang.Class object value, type must be java.lang.Class)</li>
   * </ul>
   *
   * @param type type of the constant
   * @return a DfaTypeValue whose type is DfConstantType that corresponds to given constant.
   */
  public DfaTypeValue getConstant(Object value, @NotNull PsiType type) {
    return fromDfType(DfTypes.constant(value, type));
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public DfaTypeValue fromDfType(@NotNull DfType dfType) {
    return myTypeValueFactory.create(dfType);
  }

  public Collection<DfaValue> getValues() {
    return Collections.unmodifiableCollection(myValues);
  }

  @NotNull
  public DfaControlTransferValue controlTransfer(TransferTarget kind, FList<Trap> traps) {
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

  @NotNull
  public DfaVariableValue.Factory getVarFactory() {
    return myVarFactory;
  }

  @NotNull
  public DfaWrappedValue.Factory getWrapperFactory() {
    return myBoxedFactory;
  }

  @NotNull
  public DfaBinOpValue.Factory getBinOpFactory() {
    return myBinOpFactory;
  }

}
