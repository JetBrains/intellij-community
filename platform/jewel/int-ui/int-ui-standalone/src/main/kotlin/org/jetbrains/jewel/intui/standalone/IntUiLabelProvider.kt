// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone

import org.jetbrains.jewel.ui.util.LabelProvider

public class IntUiLabelProvider : LabelProvider {
    override fun fetchLabelFromKey(key: String): String =
        when (key) {
            "action.text.open.link.in.browser" -> "Open Link in Browser"
            "action.text.copy.link.address" -> "Copy Link Address"
            else -> ""
        }
}
