// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.annotations.ApiStatus

/**
 * A layout fact the dev-distribution plan cannot state, found by the layout bindings or the descriptor plan.
 *
 * The run stops on it for a bundled and an additional plugin alike. The message names the plugin and the fact.
 */
@ApiStatus.Internal
class DevDistUnplannableLayoutException(message: String) : IllegalStateException(message)
