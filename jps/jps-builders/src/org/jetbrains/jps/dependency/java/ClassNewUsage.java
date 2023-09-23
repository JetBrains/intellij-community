// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;

public final class ClassNewUsage extends ClassUsage {

  public ClassNewUsage(@NotNull String className) {
    super(className);
  }

  @Override
  public int hashCode() {
    return super.hashCode() + 2;
  }
}
