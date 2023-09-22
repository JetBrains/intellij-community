package com.intellij.execution.multilaunch.design.popups

import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener

class SelectorPopupProvidersTracker : ContainerListener {
  val providers = mutableListOf<SelectorPopupProvider>()
  override fun componentAdded(event: ContainerEvent?) {
    val addedChild = event?.child ?: return
    val container = addedChild as? SelectorPopupsContainer ?: return
    providers.addAll(container.getSelectorPopupProviders())
  }

  override fun componentRemoved(event: ContainerEvent?) {
    val removedChild = event?.child ?: return
    val container = removedChild as? SelectorPopupsContainer ?: return
    providers.removeAll(container.getSelectorPopupProviders())
  }
}