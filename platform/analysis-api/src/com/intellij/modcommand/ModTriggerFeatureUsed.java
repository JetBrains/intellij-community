// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Triggers feature usage to collect statistics.
 * Should be ignored when the action is not actually applied (e.g., to generate preview)
 *
 * @param featureId feature identifier.
 */
@ApiStatus.Experimental
public record ModTriggerFeatureUsed(@NonNls @NotNull String featureId) implements ModCommand {

}
