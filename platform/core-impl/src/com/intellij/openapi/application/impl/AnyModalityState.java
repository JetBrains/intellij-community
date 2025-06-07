// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

public final class AnyModalityState extends ModalityState {
  public static final AnyModalityState ANY = new AnyModalityState();

  private AnyModalityState() {
  }

  @Override
  public boolean accepts(@NotNull ModalityState requestedModality) {
    // There is no point in checking whether a computation can be run in `any` modality
    // because `any` modality never happens, i.e., the current modality is never `any`.
    // Such check is most likely a programming error.
    // TODO add logging
    return true;
  }

  @Override
  public String toString() {
    return "ModalityState.ANY";
  }
}
