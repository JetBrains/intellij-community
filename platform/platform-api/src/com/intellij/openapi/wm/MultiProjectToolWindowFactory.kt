// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import org.jetbrains.annotations.ApiStatus

/**
 * Marks a tool window that needs no project selector in a frame that shows several projects.
 * Such a tool window presents every project of its frame itself, or its content does not depend on a project.
 *
 * The frame owner registers the tool window once. A tool window without the marker gets one instance for each project
 * of the frame, and the frame shows the instance of the project that the user selects in the tool window.
 */
@ApiStatus.Internal
interface MultiProjectToolWindowFactory : ToolWindowFactory
