// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp

import org.jetbrains.annotations.ApiStatus

// TODO a very dirty hack, convert to some service
@ApiStatus.Internal
val PREDEFINED_IDS: Set<String> = hashSetOf(BUNDLED_CODEX_AGENT_ID)
@ApiStatus.Internal
const val BUNDLED_CODEX_AGENT_ID: String = "codex"
