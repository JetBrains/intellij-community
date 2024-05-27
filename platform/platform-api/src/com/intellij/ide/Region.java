// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import org.jetbrains.annotations.ApiStatus;

/**
 * Region codes used for external settings, must never be changed.
 * Only add new and deprecated existing.
 */
@ApiStatus.Experimental
public enum Region {
  NOT_SET,
  AFRICA,
  AMERICA,
  ASIA,
  CHINA,
  EUROPE,
  OTHER // Rest of the World
}
