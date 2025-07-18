// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.ApiStatus;

/**
 * This interface is utilized to determine whether the insertion of a lookup element
 * into the currently active completion session should be stopped.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public interface LookupElementInsertStopper {
  boolean shouldStopLookupInsertion();
}
