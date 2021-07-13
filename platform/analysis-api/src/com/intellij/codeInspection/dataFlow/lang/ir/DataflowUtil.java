// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;

public class DataflowUtil {
  /**
   * Checks whether supplied variable is a temporary variable created previously via {@link ControlFlow#createTempVariable(DfType)}
   *
   * @param variable to check
   * @return true if supplied variable is a temp variable.
   */
  public static boolean isTempVariable(@NotNull DfaVariableValue variable) {
    return variable.getDescriptor() instanceof Synthetic;
  }
}
