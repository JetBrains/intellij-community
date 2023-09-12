// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.Usage;

abstract class JvmElementUsage implements Usage {

  @NotNull
  private final ReferenceID myOwner;

  JvmElementUsage(@NotNull ReferenceID owner) {
    myOwner = owner;
  }

  @Override
  public @NotNull ReferenceID getElementOwner() {
    return myOwner;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final JvmElementUsage jvmUsage = (JvmElementUsage)o;

    if (!myOwner.equals(jvmUsage.myOwner)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myOwner.hashCode();
  }
}
