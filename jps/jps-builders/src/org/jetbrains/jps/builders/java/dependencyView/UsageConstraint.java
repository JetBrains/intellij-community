// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

interface UsageConstraint {
  UsageConstraint ANY = residence -> true;

  boolean checkResidence(final int residence);

  static UsageConstraint exactMatch(int name) {
    return residence -> name == residence;
  }

  default UsageConstraint negate() {
    return residence -> !checkResidence(residence);
  }

  default UsageConstraint and(UsageConstraint c) {
    return c == ANY? this : residence -> checkResidence(residence) && c.checkResidence(residence);
  }

  default UsageConstraint or(UsageConstraint c) {
    return c == ANY? ANY : residence -> checkResidence(residence) || c.checkResidence(residence);
  }
}
