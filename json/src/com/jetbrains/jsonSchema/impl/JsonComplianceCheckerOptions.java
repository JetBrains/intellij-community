// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

public class JsonComplianceCheckerOptions {
  public static final JsonComplianceCheckerOptions RELAX_ENUM_CHECK = new JsonComplianceCheckerOptions(true, false);

  private final boolean isCaseInsensitiveEnumCheck;
  private final boolean isForceStrict;

  public JsonComplianceCheckerOptions(boolean caseInsensitiveEnumCheck) {
    this(caseInsensitiveEnumCheck, false);
  }

  private JsonComplianceCheckerOptions(boolean caseInsensitiveEnumCheck, boolean forceStrict) {
    isCaseInsensitiveEnumCheck = caseInsensitiveEnumCheck;
    isForceStrict = forceStrict;
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
}
