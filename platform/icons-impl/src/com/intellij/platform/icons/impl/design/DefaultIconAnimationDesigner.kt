// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.design

import com.intellij.platform.icons.design.IconAnimationDesigner
import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.impl.IconAnimationFrame

class DefaultIconAnimationDesigner(val rootDesigner: DefaultIconDesigner) : IconAnimationDesigner {
    private val frames = mutableListOf<IconAnimationFrame>()

    override fun frame(duration: Long, builder: IconDesigner.() -> Unit) {
        val designer = rootDesigner.createNestedDesigner()
        designer.builder()
        frames.add(IconAnimationFrame(designer.buildLayers(), duration))
    }

    fun build(): List<IconAnimationFrame> = frames
}
