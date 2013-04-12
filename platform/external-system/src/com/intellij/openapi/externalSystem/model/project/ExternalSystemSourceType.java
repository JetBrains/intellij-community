package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

/**
 * Enumerates module source types.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 5:21 PM
 */
public class ExternalSystemSourceType {

  @NotNull public static final ExternalSystemSourceType SOURCE   = new ExternalSystemSourceType("SOURCE");
  @NotNull public static final ExternalSystemSourceType TEST     = new ExternalSystemSourceType("TEST");
  @NotNull public static final ExternalSystemSourceType EXCLUDED = new ExternalSystemSourceType("EXCLUDED");

  @NotNull private final String myId;

  public ExternalSystemSourceType(@NotNull String id) {
    myId = id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalSystemSourceType type = (ExternalSystemSourceType)o;

    if (!myId.equals(type.myId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }

  @Override
  public String toString() {
    return myId;
  }
}
