// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

/**
 * Region codes used for external settings, must never be changed.
 * Only add new and deprecated existing.
 */
@ApiStatus.Experimental
public enum Region {
  NOT_SET("not_set", "title.region.not_set", 1000),
  AFRICA("africa", "title.region.africa", 0),
  AMERICAS("americas", "title.region.america", 1),
  APAC("apac", "title.region.asia", 2),
  CHINA("china", "title.region.china", 3),
  EUROPE("europe", "title.region.europe", 4),
  MIDDLE_EAST("middle_east", "title.region.middle_east", 5),
  OCEANIA("oceania", "title.region.oceania", 6);

  private final String extName;
  private final String displayKey;
  private final int displayOrdinal;

  Region(String extName, String displayKey, int displayOrdinal) {
    this.extName = extName;
    this.displayKey = displayKey;
    this.displayOrdinal = displayOrdinal;
  }

  public String externalName() {
    return extName;
  }

  public @Nls String getDisplayName() {
    return IdeBundle.message(displayKey);
  }

  public String getDisplayKey() {
    return displayKey;
  }

  public int getDisplayOrdinal() {
    return displayOrdinal;
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
