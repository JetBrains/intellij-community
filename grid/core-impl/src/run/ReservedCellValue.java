package com.intellij.database.run;

import com.intellij.openapi.util.NlsSafe;

/**
 * @author gregsh
 */
public enum ReservedCellValue {
  NULL, DEFAULT, GENERATED, COMPUTED, UNSET;

  public @NlsSafe String getDisplayName() {
    return switch (this) {
      case NULL -> "<null>";
      case DEFAULT -> "<default>";
      case GENERATED -> "<generated>";
      case COMPUTED -> "<computed>";
      case UNSET -> "<unset>";
    };
  }

  @Override
  public String toString() {
    return this == UNSET ? NULL.toString() : super.toString();
  }

  public String getSqlName() {
    return switch (this) {
      case NULL, UNSET -> "NULL";
      case DEFAULT -> "DEFAULT";
      case GENERATED -> "GENERATED";
      case COMPUTED -> "COMPUTED";
    };
  }
}
