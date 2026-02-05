// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.design

import com.intellij.platform.icons.design.IconMargin
import com.intellij.platform.icons.design.IconUnit
import kotlinx.serialization.Serializable

@Serializable
class DefaultIconMargin(
    override val top: IconUnit,
    override val left: IconUnit,
    override val bottom: IconUnit,
    override val right: IconUnit,
) : IconMargin {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IconMargin

        if (top != other.top) return false
        if (left != other.left) return false
        if (bottom != other.bottom) return false
        if (right != other.right) return false

        return true
    }

    override fun hashCode(): Int {
        var result = top.hashCode()
        result = 31 * result + left.hashCode()
        result = 31 * result + bottom.hashCode()
        result = 31 * result + right.hashCode()
        return result
    }

    override fun toString(): String = "IconMargin(top=$top, left=$left, bottom=$bottom, right=$right)"
}
