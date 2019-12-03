// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public enum DumbModeAccessType {
  RELIABLE_DATA_ONLY,
  RAW_INDEX_DATA_ACCEPTABLE
}
