// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProjectViewFileNestingModel {
  fun getRules(): List<ProjectViewFileNestingService.NestingRule>
  fun setRules(rules: List<ProjectViewFileNestingService.NestingRule>)
  fun getDefaultRules(): List<ProjectViewFileNestingService.NestingRule>
}
