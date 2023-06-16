// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom;

/**
 * Implementation of {@link Navigatable} interface which actually doesn't allow navigation. Its {@link #INSTANCE} can be passed to methods which
 * expect non-null instance of {@link Navigatable} if you cannot provide a real implementation.
 */
public final class NonNavigatable implements Navigatable {
  public static final Navigatable INSTANCE = new NonNavigatable();

  private NonNavigatable() {
  }
}
