// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.cellvalidators;

import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface TableCellValidator {
  ValidationInfo validate(Object value, int row, int column);
}
