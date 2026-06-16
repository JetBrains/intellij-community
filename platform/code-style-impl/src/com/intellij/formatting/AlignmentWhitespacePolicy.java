// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import org.jetbrains.annotations.ApiStatus;

/**
 * This policy allows enforcing using spaces for alignment that is necessary for some languages even if USE_TAB_CHARACTER option is enabled.
 * @see WhiteSpace#setForceSkipTabulationsUsage(boolean)
 */
@ApiStatus.Internal
public interface AlignmentWhitespacePolicy {
  boolean useSpacesForAlignment();
}
