// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.optionalToIf;

import com.intellij.codeInspection.streamToLoop.ChainVariable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


interface Operation {

  @NotNull
  ChainVariable getOutVar(@NotNull ChainVariable inVar);

  @Nullable
  String generate(@NotNull ChainVariable inVar, @NotNull ChainVariable outVar, @NotNull String code, @NotNull OptionalToIfContext context);

  default void rename(@NotNull String oldName, @NotNull ChainVariable newVar, @NotNull OptionalToIfContext context) {}

  default void preprocessVariables(@NotNull ChainVariable inVar, @NotNull ChainVariable outVar, @NotNull OptionalToIfContext context) {}

  default @NotNull StreamEx<OptionalToIfInspection.OperationRecord> nestedOperations() {
    return StreamEx.empty();
  }
}
