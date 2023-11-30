package com.intellij.execution.multilaunch.statistics

enum class MultiLaunchCreationOrigin {
  EDIT_CONFIGURATIONS,

  // Rider-specific
  SOLUTION_CONTEXT_MENU,
  PROJECT_CONTEXT_MENU,
  PROJECTS_CONTEXT_MENU,

  UNKNOWN,
}