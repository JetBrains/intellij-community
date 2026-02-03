// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.ApiStatus.Internal;

/**
 * Marker interface for ExtensionPointListener implementations that need to be invoked before other listeners.
 */
@Internal
public interface ExtensionPointPriorityListener {
}
