package com.intellij.microservices.endpoints

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import javax.swing.JComponent

/**
 * Represents a custom details tab in the Endpoints tool window for selected endpoints.
 */
interface EndpointsSidePanelProvider {
  fun create(project: Project): EndpointsSidePanel?
}

interface EndpointsSidePanel {
  @get:NlsContexts.TabTitle
  val title: String

  val component: JComponent

  /**
   * Determines whether the side panel is available.
   *
   * @return true if the side panel should be available; false otherwise.
   * If false, the panel may be disabled
   * or remain accessible if it has been previously selected by the user to avoid unnecessary flickering.
   */
  suspend fun isAvailable(selectedItems: List<EndpointsListItem>): Boolean

  /**
   * Updates the component for the new list of selected endpoints items.
   *
   * The update will be canceled if the selected endpoints (@param selectedItems) change or if there are psi changes.
   * The update will not be called if the tab has already been updated.
   * The update may also be called when [com.intellij.microservices.ui.flat.EndpointsView.forgetData] is invoked.
   */
  suspend fun update(selectedItems: List<EndpointsListItem>)

  /**
   * Method called when the side panel is shown but not [update].
   * For example, this can be invoked if the panel was already updated before.
   */
  fun selected(selectedItems: List<EndpointsListItem>) {}
}