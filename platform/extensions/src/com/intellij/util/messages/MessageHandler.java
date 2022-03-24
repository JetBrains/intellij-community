// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;

/**
 * Defines contract for generic message subscriber processor.
 */
public interface MessageHandler {
  /**
   * Is called on new message arrival. Given method identifies method used by publisher (see {@link Topic#getListenerClass()}),
   * given parameters were used by the publisher during target method call.
   *
   * @param event   information about target method called by the publisher
   * @param params  called method arguments
   */
  void handle(@NotNull MethodHandle event, Object... params);
}