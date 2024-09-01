// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public enum IntentionSource {
  DAEMON_TOOLTIP,
  CONTEXT_ACTIONS,
  LIGHT_BULB,
  FLOATING_TOOLBAR,
  FILE_LEVEL_ACTIONS,
  PROBLEMS_VIEW
}
