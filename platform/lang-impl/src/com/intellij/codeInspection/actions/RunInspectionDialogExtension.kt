// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions

import com.intellij.codeInspection.options.OptionContainer
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point for adding custom UI components to the "Run Inspection by Name" dialog.
 * Allows plugins to contribute checkboxes or other settings to the inspection dialog
 * using a declarative approach via [OptPane].
 *
 * Extensions should:
 * 1. Override [getOptionsPane] to declare UI options (checkboxes, etc.)
 * 2. Have fields matching the bindIds in the options (e.g., `var myOption = false`)
 * 3. Read field values in [beforeInspectionRun] to apply settings
 */
@ApiStatus.Experimental
interface RunInspectionDialogExtension : OptionContainer {
  /**
   * Called before the inspection is run. Extensions can prepare their services here.
   *
   * @param project the current project
   */
  fun beforeInspectionRun(project: Project) {}
}
