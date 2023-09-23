// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

public final class JsonComplianceCheckerOptions {
  public static final JsonComplianceCheckerOptions RELAX_ENUM_CHECK = new JsonComplianceCheckerOptions(true, false);

  private final boolean isCaseInsensitiveEnumCheck;
  private final boolean isForceStrict;

  private final boolean isReportMissingOptionalProperties;

  public JsonComplianceCheckerOptions(boolean caseInsensitiveEnumCheck) {
    this(caseInsensitiveEnumCheck, false);
  }

  private JsonComplianceCheckerOptions(boolean caseInsensitiveEnumCheck, boolean forceStrict) {
    this(caseInsensitiveEnumCheck, forceStrict, false);
  }

  public JsonComplianceCheckerOptions(boolean isCaseInsensitiveEnumCheck,
                                      boolean isForceStrict,
                                      boolean isReportMissingOptionalProperties) {
    this.isCaseInsensitiveEnumCheck = isCaseInsensitiveEnumCheck;
    this.isForceStrict = isForceStrict;
    this.isReportMissingOptionalProperties = isReportMissingOptionalProperties;
  }

  public JsonComplianceCheckerOptions withForcedStrict() {
    return new JsonComplianceCheckerOptions(isCaseInsensitiveEnumCheck, true);
  }

  public boolean isCaseInsensitiveEnumCheck() {
    return isCaseInsensitiveEnumCheck;
  }

  public boolean isForceStrict() {
    return isForceStrict;
  }

  public boolean isReportMissingOptionalProperties() {
    return isReportMissingOptionalProperties;
  }
}
