package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.Nullable;

/**
 * Enumerates module source types.
 * 
 * @author Denis Zhdanov
 * @since 8/10/11 5:21 PM
 */
public enum ExternalSystemSourceType implements IExternalSystemSourceType {

  SOURCE(false, false, false, false),
  TEST(true, false, false, false),
  EXCLUDED(false, false, false, true),
  SOURCE_GENERATED(false, true, false, false),
  TEST_GENERATED(true, true, false, false),
  RESOURCE(false, false, true, false),
  TEST_RESOURCE(true, false, true, false);

  private final boolean isTest;
  private final boolean isGenerated;
  private final boolean isResource;
  private final boolean isExcluded;

  ExternalSystemSourceType(boolean test, boolean generated, boolean resource, boolean excluded) {
    isTest = test;
    isGenerated = generated;
    isResource = resource;
    isExcluded = excluded;
  }

  @Override
  public boolean isTest() {
    return isTest;
  }

  @Override
  public boolean isGenerated() {
    return isGenerated;
  }

  @Override
  public boolean isResource() {
    return isResource;
  }

  @Override
  public boolean isExcluded() {
    return isExcluded;
  }

  public static ExternalSystemSourceType from(IExternalSystemSourceType sourceType) {
    for (ExternalSystemSourceType systemSourceType : ExternalSystemSourceType.values()) {
      if (systemSourceType.isGenerated == sourceType.isGenerated() &&
          systemSourceType.isResource == sourceType.isResource() &&
          systemSourceType.isTest == sourceType.isTest() &&
          systemSourceType.isExcluded == sourceType.isExcluded()) {
        return systemSourceType;
      }
    }
    throw new IllegalArgumentException("Invalid source type: " + sourceType);
  }

  @Nullable
  public static ExternalSystemSourceType from(boolean isTest,
                                              boolean isGenerated,
                                              boolean isResource,
                                              boolean isExcluded) {
    for (ExternalSystemSourceType systemSourceType : ExternalSystemSourceType.values()) {
      if (systemSourceType.isGenerated == isGenerated &&
          systemSourceType.isResource == isResource &&
          systemSourceType.isTest == isTest &&
          systemSourceType.isExcluded == isExcluded) {
        return systemSourceType;
      }
    }
    return null;
  }
}
