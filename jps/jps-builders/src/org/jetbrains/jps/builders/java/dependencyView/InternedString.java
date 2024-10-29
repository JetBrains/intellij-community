// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import org.jetbrains.annotations.NotNull;

abstract class InternedString {
  public abstract @NotNull DependencyContext getContext();

  public abstract int asInt();

  public abstract String asString();

  public static InternedString create(final DependencyContext context, final String val) {
    return new InternedString() {
      @Override
      public DependencyContext getContext() {
        return context;
      }

      @Override
      public int asInt() {
        return context.get(val);
      }

      @Override
      public String asString() {
        return val;
      }
    };
  }

  public static InternedString create(final DependencyContext context, final int val) {
    return new InternedString() {
      @Override
      public @NotNull DependencyContext getContext() {
        return context;
      }

      @Override
      public int asInt() {
        return val;
      }

      @Override
      public String asString() {
        return getContext().getValue(val);
      }
    };
  }
}
