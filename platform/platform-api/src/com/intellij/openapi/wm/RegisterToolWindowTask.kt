// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm

import javax.swing.JComponent

data class RegisterToolWindowTask @JvmOverloads constructor(val id: String,
                                                            val component: JComponent?,
                                                            val anchor: ToolWindowAnchor,
                                                            val sideTool: Boolean = false,
                                                            val canCloseContent: Boolean = true,
                                                            val canWorkInDumbMode: Boolean = true,
                                                            val shouldBeAvailable: Boolean = true)