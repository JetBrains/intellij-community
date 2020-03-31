// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  default void rename(@NotNull String oldName, @NotNull String newName, @NotNull OptionalToIfContext context) {}

  default void preprocessVariables(@NotNull ChainVariable inVar, @NotNull ChainVariable outVar, @NotNull OptionalToIfContext context) {}

  @NotNull
  default StreamEx<OptionalToIfInspection.OperationRecord> nestedOperations() {
    return StreamEx.empty();
  }
}
