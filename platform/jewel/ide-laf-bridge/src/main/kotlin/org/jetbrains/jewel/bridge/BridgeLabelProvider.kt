// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import com.intellij.ide.IdeBundle
import org.jetbrains.jewel.ui.util.LabelProvider

public class BridgeLabelProvider : LabelProvider {
    override fun fetchLabelFromKey(key: String): String = IdeBundle.messagePointer(key).get()
}
