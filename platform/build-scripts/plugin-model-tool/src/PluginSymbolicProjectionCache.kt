// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.pluginModelTool

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.JpsProject

/**
 * The answers that every projection of [project] shares.
 *
 * A generator run projects thousands of layouts over one project. Concurrent projections may share it.
 */
@ApiStatus.Internal
class PluginSymbolicProjectionCache(@JvmField val project: JpsProject)
