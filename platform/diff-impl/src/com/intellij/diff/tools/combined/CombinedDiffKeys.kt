// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Key

val COMBINED_DIFF_VIEWER = DataKey.create<CombinedDiffViewer>("combined_diff_viewer")
val COMBINED_DIFF_VIEWER_KEY = Key.create<CombinedDiffViewer>("combined_diff_viewer")
val COMBINED_DIFF_MAIN_UI = Key.create<CombinedDiffMainUI>("combined_diff_main_ui")
val COMBINED_DIFF_MODEL = Key.create<CombinedDiffModel>("combined_diff_model")
val COMBINED_DIFF_SCROLL_TO_BLOCK = Key.create<CombinedBlockId>("combined_diff_scroll_to_block")
