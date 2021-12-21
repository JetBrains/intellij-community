/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.actionSystem.AnAction

/** Interface indicates the possibility to create the action tool bars. */
interface ToolBarProvider {
    fun createActions(): List<AnAction>
}