// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * The FusReportableAction interface represents an action that can be reported to FUS (Feature Usage Statistics).
 * Use this interface for actions which are not registered within {@link com.intellij.openapi.actionSystem.ActionManager} (e.g., represented by lambda/anonymous classes)
 *
 * @see com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionIdProvider
 */
@ApiStatus.Internal
public interface FusReportableAction {
  /**
   * @return unique ID reported to FUS
   */
  @NotNull
  @NonNls
  String getId();
}
