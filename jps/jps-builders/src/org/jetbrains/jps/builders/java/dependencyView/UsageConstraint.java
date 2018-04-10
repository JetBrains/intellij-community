// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

/**
 * @author Eugene Zhuravlev
 */
public interface UsageConstraint {
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
