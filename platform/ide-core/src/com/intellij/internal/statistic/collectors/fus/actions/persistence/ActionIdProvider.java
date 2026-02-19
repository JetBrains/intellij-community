// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import org.jetbrains.annotations.ApiStatus;

/**
 * The interface provides an action ID that can be reported to Feature Usage Statistics.
 * Use this interface for actions which are not registered within {@link com.intellij.openapi.actionSystem.ActionManager}
 * (e.g., represented by lambda/anonymous classes).
 */
@ApiStatus.Internal
public interface ActionIdProvider {
  String getId();
}
