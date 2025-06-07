// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import org.jetbrains.annotations.NotNull;

/**
 * A node in the reference graph corresponding to the implicit constructor of a Java class.
 *
 * @author anna
 */
public interface RefImplicitConstructor extends RefMethod {
  @Override
  default @NotNull RefClass getOwnerClass() {
    throw new UnsupportedOperationException();
  }
}
