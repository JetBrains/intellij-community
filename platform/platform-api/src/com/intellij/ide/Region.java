// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import org.jetbrains.annotations.ApiStatus;

/**
 * Region codes used for external settings, must never be changed.
 * Only add new and deprecated existing.
 */
@ApiStatus.Experimental
public enum Region {
  NOT_SET("not_set"),
  AFRICA("africa"),
  AMERICA("america"),
  ASIA("asia"),
  CHINA("china"),
  EUROPE("europe");

  private final String extName;

  Region(String extName) {
    this.extName = extName;
  }

  public String externalName() {
    return extName;
  }

  public static Region fromExternalName(String extName) {
    for (Region value : values()) {
      if (value.extName.equals(extName)) {
        return value;
      }
    }
    return NOT_SET;
  }
}
