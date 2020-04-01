// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr;

/**
 * Data structure describing all possible Consent JSON attributes
 * Date: 06-Dec-17
 */
final class ConsentAttributes {
  String consentId;
  String version;
  String text;
  String printableName;
  boolean accepted;
  boolean deleted;
  long acceptanceTime;
}
