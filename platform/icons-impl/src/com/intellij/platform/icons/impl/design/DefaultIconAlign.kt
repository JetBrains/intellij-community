// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.design

import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.IconHorizontalAlign
import com.intellij.platform.icons.design.IconVerticalAlign
import kotlinx.serialization.Serializable

@Serializable
class DefaultIconAlign(
    override val verticalAlign: IconVerticalAlign,
    override val horizontalAlign: IconHorizontalAlign,
) : IconAlign {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultIconAlign

        if (verticalAlign != other.verticalAlign) return false
        if (horizontalAlign != other.horizontalAlign) return false

        return true
    }

    override fun hashCode(): Int {
        var result = verticalAlign.hashCode()
        result = 31 * result + horizontalAlign.hashCode()
        return result
    }

    override fun toString(): String = "DefaultIconAlign(verticalAlign=$verticalAlign, horizontalAlign=$horizontalAlign)"
}
