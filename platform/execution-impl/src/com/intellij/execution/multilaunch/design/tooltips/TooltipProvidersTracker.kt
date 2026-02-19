package com.intellij.execution.multilaunch.design.tooltips

import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener

class TooltipProvidersTracker : ContainerListener {
  val providers = mutableListOf<TooltipProvider>()
  override fun componentAdded(event: ContainerEvent?) {
    val addedChild = event?.child ?: return
    val container = addedChild as? TooltipProvidersContainer ?: return
    providers.addAll(container.getTooltipProviders())
  }

  override fun componentRemoved(event: ContainerEvent?) {
    val removedChild = event?.child ?: return
    val container = removedChild as? TooltipProvidersContainer ?: return
    providers.removeAll(container.getTooltipProviders())
  }
}

