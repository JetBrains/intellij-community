package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.NotebookInlayMouseListener.Companion.topic
import java.awt.AWTEvent
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JLayer
import javax.swing.JPanel
import javax.swing.plaf.LayerUI

/**
 * Sends mouse click events to the [topic] each time user clicks on inlay
 */
@FunctionalInterface
fun interface NotebookInlayMouseListener : EventListener {
  companion object {
    val topic = Topic(NotebookInlayMouseListener::class.java)

    /**
     * Wraps panel with layer to catch all events
     */
    internal fun wrapPanel(panel: JPanel, editor: Editor): JLayer<JPanel> =
      JLayer(panel).apply {
        layerEventMask = AWTEvent.MOUSE_EVENT_MASK
        setUI(object : LayerUI<JPanel>() {
          override fun processMouseEvent(e: MouseEvent, l: JLayer<out JPanel>) {
            if (e.id == MouseEvent.MOUSE_CLICKED) {
              ApplicationManager.getApplication().messageBus.syncPublisher(topic).mouseClicked(e, editor)
            }
          }
        })
      }
  }

  /**
   * User clicked on inlay output
   * @param mouseEvent mose event sent to inlay
   * @param editor Since all listeners get all events, we send editor in which event happened
   */
  fun mouseClicked(mouseEvent: MouseEvent, editor: Editor)
}

