// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class UseOfConcreteClassMerger extends InspectionElementsMergerBase {
  @Override
  public @NotNull String getMergedToolName() {
    return "UseOfConcreteClass";
  }

  @Override
  public @NonNls String @NotNull [] getSourceToolNames() {
    return new String[]{"LocalVariableOfConcreteClass", "ParameterOfConcreteClass", "MethodReturnOfConcreteClass", "CastToConcreteClass",
      "StaticVariableOfConcreteClass", "InstanceVariableOfConcreteClass", "InstanceofConcreteClass"};
  }

  @Override
  public @NonNls String @NotNull [] getSuppressIds() {
    return new String[] {"LocalVariableOfConcreteClass", "ParameterOfConcreteClass", "MethodParameterOfConcreteClass",
      "MethodReturnOfConcreteClass", "CastToConcreteClass", "StaticVariableOfConcreteClass", "InstanceVariableOfConcreteClass",
      "InstanceofConcreteClass", "InstanceofInterfaces"};
  }

  @Override
  protected boolean isEnabledByDefault(@NotNull String sourceToolName) {
    return false;
  }

  @Override
  protected boolean writeMergedContent(@NotNull Element toolElement) {
    return true;
  }
}
