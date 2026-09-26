// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author gregsh
 */
@ApiStatus.Internal
public class SlowOperationCanceledException extends ProcessCanceledException {
}
