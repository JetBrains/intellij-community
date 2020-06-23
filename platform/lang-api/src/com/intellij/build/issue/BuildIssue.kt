// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.issue

import com.intellij.build.events.BuildEventsNls
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
interface BuildIssue {
  val title: @BuildEventsNls.Title String
  val description: @BuildEventsNls.Description String
  val quickFixes: List<BuildIssueQuickFix>
  fun getNavigatable(project: Project): Navigatable?
}
