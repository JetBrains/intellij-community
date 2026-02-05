// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.icon

import org.jetbrains.icons.Icon
import org.jetbrains.icons.design.IconDesigner
import org.jetbrains.icons.impl.DefaultIconManager

internal class StandaloneIconManager : DefaultIconManager() {
    override fun icon(designer: IconDesigner.() -> Unit): Icon {
        val iconDesigner = StandaloneIconDesigner()
        iconDesigner.designer()
        return iconDesigner.build()
    }
}
