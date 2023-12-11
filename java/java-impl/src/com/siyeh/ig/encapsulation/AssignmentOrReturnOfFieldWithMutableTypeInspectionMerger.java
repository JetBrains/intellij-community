// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.ex.InspectionElementsMerger;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class AssignmentOrReturnOfFieldWithMutableTypeInspectionMerger extends InspectionElementsMerger {

  @NotNull
  @Override
  public String getMergedToolName() {
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
