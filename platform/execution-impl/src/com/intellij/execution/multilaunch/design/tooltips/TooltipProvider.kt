package com.intellij.execution.multilaunch.design.tooltips

import javax.swing.JComponent

interface TooltipProvider {
  val tooltipTarget: JComponent
  val tooltipText: String
}