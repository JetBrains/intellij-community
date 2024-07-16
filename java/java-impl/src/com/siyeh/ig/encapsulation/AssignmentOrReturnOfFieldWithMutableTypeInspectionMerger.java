// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class AssignmentOrReturnOfFieldWithMutableTypeInspectionMerger extends InspectionElementsMerger {

  @Override
  public @NotNull String getMergedToolName() {
    return "AssignmentOrReturnOfFieldWithMutableType";
  }

  @Override
  public String @NotNull [] getSourceToolNames() {
    return new String[] {
      "AssignmentToCollectionFieldFromParameter",
      "AssignmentToDateFieldFromParameter",
      "ReturnOfCollectionField",
      "ReturnOfDateField"
    };
  }

  @Override
  public String @NotNull [] getSuppressIds() {
    return new String[] {
      "AssignmentToCollectionOrArrayFieldFromParameter",
      "AssignmentToDateFieldFromParameter",
      "ReturnOfCollectionOrArrayField",
      "ReturnOfDateField"
    };
  }
}
