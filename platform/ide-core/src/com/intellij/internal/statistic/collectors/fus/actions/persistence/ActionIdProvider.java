// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

/**
 * The ActionIdProvider interface provides action id that can be reported to FUS (Feature Usage Statistics).
 * Use this interface for actions which are not registered within {@link com.intellij.openapi.actionSystem.ActionManager} (e.g., represented by lambda/anonymous classes)
 */
@ApiStatus.Internal
public interface ActionIdProvider {
  @NonNls
  String getId();
}
