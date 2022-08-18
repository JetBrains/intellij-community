// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr;

/**
 * Data structure describing all possible Consent JSON attributes
 */
final class ConsentAttributes {
  public String consentId;
  public String version;
  public String text;
  public String printableName;
  public boolean accepted;
  public boolean deleted;
  public long acceptanceTime;
}
