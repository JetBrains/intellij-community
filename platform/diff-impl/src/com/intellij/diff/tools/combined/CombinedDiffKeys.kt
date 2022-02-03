// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Key

val COMBINED_DIFF_VIEWER = DataKey.create<CombinedDiffViewer>("combined_diff_viewer")
val COMBINED_DIFF_PROCESSOR = Key.create<CombinedDiffRequestProcessor>("combined_diff_processor")
