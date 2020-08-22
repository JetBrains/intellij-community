// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public final class AnyModalityState extends ModalityState {
  public static final AnyModalityState ANY = new AnyModalityState();

  private AnyModalityState() {
  }

  @Override
  public boolean dominates(@NotNull ModalityState anotherState) {
    return false;
  }

  @Override
  public String toString() {
    return "ANY";
  }
}
