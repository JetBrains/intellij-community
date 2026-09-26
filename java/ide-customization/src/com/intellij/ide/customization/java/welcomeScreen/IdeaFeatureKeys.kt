// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customization.java.welcomeScreen

internal object IdeaFeatureKeys {
  const val TERMINAL = "terminal.toolwindow"

  const val NEW_FILE = "New.File"

  /** Owned by the Air plugin, which supplies both halves of the feature. See `AGENT_SESSIONS_WELCOME_FEATURE_KEY`. */
  const val AIR_SESSIONS = "air.sessions"
}
