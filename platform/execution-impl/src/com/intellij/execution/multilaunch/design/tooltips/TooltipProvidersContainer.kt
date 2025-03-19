package com.intellij.execution.multilaunch.design.tooltips

interface TooltipProvidersContainer {
  fun getTooltipProviders(): List<TooltipProvider>
}
