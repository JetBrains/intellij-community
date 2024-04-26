// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * This class does not reflect a real-member or a real-class usage.
 * Add this usage to the set of affected usages to tag additional nodes, whose dependencies should be treated as potentially affected
 */
public final class AffectionScopeMetaUsage implements Usage {
  private final ReferenceID myNodeId;

  public AffectionScopeMetaUsage(@NotNull ReferenceID nodeId) {
    myNodeId = nodeId;
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    // not supported
  }

  @Override
  public @NotNull ReferenceID getElementOwner() {
    return myNodeId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final AffectionScopeMetaUsage that = (AffectionScopeMetaUsage)o;

    if (!myNodeId.equals(that.myNodeId)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myNodeId.hashCode();
  }
}
