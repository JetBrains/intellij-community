// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface SpecialField extends VariableDescriptor {
  /**
   * @param fieldValue dfType of the special field value
   * @return a dfType that represents a value having this special field restricted to the supplied dfType
   */
  @NotNull DfType asDfType(@NotNull DfType fieldValue);

  /**
   * @param fieldValue dfType of the special field value
   * @param project project
   * @return a dfType that represents a value having this special field restricted to the supplied dfType.
   * Unlike {@link #asDfType(DfType)} this overload may canonicalize some values.
   */
  @NotNull DfType asDfType(@NotNull DfType fieldValue, @NotNull Project project);

  /**
   * Returns a DfType from given DfType qualifier if it's bound to this special field
   * @param dfType of the qualifier
   * @return en extracted DfType
   */
  @NotNull DfType getFromQualifier(@NotNull DfType dfType);
}
