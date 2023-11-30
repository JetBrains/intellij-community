// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;

public final class ParamAnnotation  {
  public final int paramIndex;
  public final @NotNull TypeRepr.ClassType type;

  public ParamAnnotation(int paramIndex, @NotNull TypeRepr.ClassType type) {
    this.paramIndex = paramIndex;
    this.type = type;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ParamAnnotation that = (ParamAnnotation)o;

    if (paramIndex != that.paramIndex) return false;
    if (!type.equals(that.type)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = paramIndex;
    result = 31 * result + type.hashCode();
    return result;
  }
}
