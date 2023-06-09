// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.actions

import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.QuickFix
import com.intellij.openapi.project.Project

open class PerformFixesTask(project: Project, descriptors: List<CommonProblemDescriptor>, quickFixClass: Class<*>?) :
  AbstractPerformFixesTask(project, descriptors.toTypedArray(), quickFixClass) {

  override fun <D : CommonProblemDescriptor> collectFix(fix: QuickFix<D>, descriptor: D, project: Project): Unit =
    fix.applyFix(project, descriptor)
}