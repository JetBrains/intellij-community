// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.util.RefComparisonChange

data class CodeReviewChangeList(val commitSha: String?, val changes: List<RefComparisonChange>)