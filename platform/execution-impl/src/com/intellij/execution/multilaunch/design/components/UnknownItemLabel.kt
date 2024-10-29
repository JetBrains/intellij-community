package com.intellij.execution.multilaunch.design.components

import com.intellij.execution.ExecutionBundle
import com.intellij.icons.AllIcons
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.execution.multilaunch.design.tooltips.TooltipProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class UnknownItemLabel(private val tooltip: String) : SimpleColoredComponent(), TooltipProvider {
  constructor() : this(TEXT.replace("<", "&lt;").replace(">", "&gt;"))

  companion object {
    private val TEXT by lazy { ExecutionBundle.message("run.configurations.multilaunch.table.row.unknown") }
  }

  init {
    icon = AllIcons.Ide.FatalError
    appendWithClipping(TEXT, SimpleTextAttributes.ERROR_ATTRIBUTES, DefaultFragmentTextClipper.INSTANCE)
  }

  override val tooltipTarget get() = this
  override val tooltipText get() = tooltip
}