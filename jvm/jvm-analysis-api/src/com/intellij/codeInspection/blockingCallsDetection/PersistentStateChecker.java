// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import org.jetbrains.annotations.ApiStatus;

/**
 * Marks extension points {@link BlockingMethodChecker} or {@link NonBlockingContextChecker} that uses externally stored settings
 * and due to that can not be used in {@link com.intellij.psi.util.CachedValuesManager}
 * <p>
 * Likely for internal usage since third party extensions may not have access to inspection settings etc
 */
@ApiStatus.Experimental
public interface PersistentStateChecker {
}
