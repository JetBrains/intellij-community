// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class MethodDoesntCallSuperMethodInspectionMerger extends InspectionElementsMerger {

  @Override
  public @NotNull String getMergedToolName() {
    return "RefusedBequest";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {
      "RefusedBequest",
      "CloneCallsSuperClone",
      "SetupCallsSuperSetUp",
      "TeardownCallsSuperTearDown",
      "FinalizeCallsSuperFinalize"
    };
  }

  @Override
  public String @NotNull [] getSuppressIds() {
    return new String[] {
      "CloneDoesntCallSuperClone",
      "SetUpDoesntCallSuperSetUp",
      "TearDownDoesntCallSuperTearDown",
      "FinalizeDoesntCallSuperFinalize"};
  }
}
