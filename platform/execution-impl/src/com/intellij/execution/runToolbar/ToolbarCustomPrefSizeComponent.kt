package com.intellij.execution.runToolbar

import com.intellij.openapi.project.Project
import java.awt.Dimension

interface ToolbarCustomPrefSizeComponent {
  fun getPreferredSize(project: Project): Dimension?
}