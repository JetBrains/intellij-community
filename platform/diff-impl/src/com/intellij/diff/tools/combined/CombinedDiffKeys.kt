// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
val COMBINED_DIFF_VIEWER = DataKey.create<CombinedDiffViewer>("combined_diff_viewer")
@ApiStatus.Experimental
val COMBINED_DIFF_VIEWER_KEY = Key.create<CombinedDiffViewer>("combined_diff_viewer")
@ApiStatus.Experimental
val COMBINED_DIFF_MAIN_UI = Key.create<CombinedDiffMainUI>("combined_diff_main_ui")
@ApiStatus.Experimental
val COMBINED_DIFF_SCROLL_TO_BLOCK = Key.create<CombinedBlockId>("combined_diff_scroll_to_block")
@ApiStatus.Experimental
val COMBINED_DIFF_VIEWER_INITIAL_FOCUS_REQUEST = Key.create<Boolean>("combined_diff_viewer_initial_focus_request")

@ApiStatus.Experimental
val DISABLE_LOADING_BLOCKS = Key.create<Boolean>("combined_diff_viewer_dont_show_loading_blocks")
