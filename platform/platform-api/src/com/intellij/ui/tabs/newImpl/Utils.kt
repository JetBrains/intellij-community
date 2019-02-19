// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl

import com.intellij.openapi.rd.childAtMouse
import com.jetbrains.rd.util.reactive.map
import javax.swing.JComponent

fun JComponent.getTabLabelUnderMouse() = this@getTabLabelUnderMouse.childAtMouse().map { if (it is TabLabel) it else null }
