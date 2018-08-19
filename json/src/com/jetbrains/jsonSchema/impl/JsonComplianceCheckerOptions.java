// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

public class JsonComplianceCheckerOptions {
  public static final JsonComplianceCheckerOptions RELAX_ENUM_CHECK = new JsonComplianceCheckerOptions(true);

  private final boolean isCaseInsensitiveEnumCheck;
  public JsonComplianceCheckerOptions(boolean caseInsensitiveEnumCheck) {isCaseInsensitiveEnumCheck = caseInsensitiveEnumCheck;}

  public boolean isCaseInsensitiveEnumCheck() {
    return isCaseInsensitiveEnumCheck;
  }
}
