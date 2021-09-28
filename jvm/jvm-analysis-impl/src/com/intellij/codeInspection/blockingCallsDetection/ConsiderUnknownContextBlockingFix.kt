// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project

class ConsiderUnknownContextBlockingFix(@IntentionName private val intentionName: String): LocalQuickFix {
  override fun getFamilyName(): String {
    return intentionName
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {

  }
}