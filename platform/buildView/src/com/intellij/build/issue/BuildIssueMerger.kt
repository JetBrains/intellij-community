// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.issue

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.OverrideOnly

@Internal
@OverrideOnly
interface BuildIssueMerger {

  fun isApplicable(issue: BuildIssue): Boolean

  fun merge(issues: List<BuildIssue>): List<BuildIssue>
}