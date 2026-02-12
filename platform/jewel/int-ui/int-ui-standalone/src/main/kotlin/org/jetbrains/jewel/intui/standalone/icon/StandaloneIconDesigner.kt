// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.icon

import org.jetbrains.icons.impl.design.DefaultIconDesigner
import org.jetbrains.icons.modifiers.IconModifier
import org.jetbrains.jewel.ui.icon.PathImageResourceLocation

internal class StandaloneIconDesigner : DefaultIconDesigner() {
    override fun image(path: String, classLoader: ClassLoader?, modifier: IconModifier) {
        image(PathImageResourceLocation(path, classLoader), modifier)
    }

    override fun createNestedDesigner(): StandaloneIconDesigner = StandaloneIconDesigner()
}
