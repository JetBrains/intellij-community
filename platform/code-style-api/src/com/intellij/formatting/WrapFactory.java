// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import org.jetbrains.annotations.ApiStatus;

/**
 * Internal interface for creating wrap setting instances.
 */
@ApiStatus.Internal
public interface WrapFactory {
  Wrap createWrap(WrapType type, boolean wrapFirstElement);

  Wrap createChildWrap(Wrap parentWrap, WrapType wrapType, boolean wrapFirstElement);
}
