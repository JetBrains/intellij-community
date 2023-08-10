package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.util.ColorProgressBar
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.notebooks.visualization.r.ui.UiCustomizer
import java.awt.BorderLayout
import java.awt.Color
import java.time.LocalDateTime
import java.time.ZonedDateTime
import javax.swing.*

private val TEXT_BORDER = JBUIScale.scale(5)
val PROGRESS_BAR_DEFAULT_WIDTH = JBUIScale.scale(4)

enum class ProgressStatus {
  RUNNING, STOPPED_OK, STOPPED_ERROR
}

/**
 * @param previousStatusDateTime - date time, when previous status was set
 * @param statusStartDateTime - date time, when status was set
 */
data class StatusDateTimeStamp(val previousStatusDateTime: ZonedDateTime? = null, val statusStartDateTime: ZonedDateTime)

data class InlayProgressStatus(val progress: ProgressStatus,
                               @Nls val statusText: String = "",
                               val statusDateTimeStamp: StatusDateTimeStamp? = null)

fun buildProgressStatusComponent(progressStatus: InlayProgressStatus, editor: Editor): JComponent? {
  if (progressStatus.progress == ProgressStatus.STOPPED_OK && progressStatus.statusText.isEmpty()) return null
  val progressPanel = JPanel(BorderLayout()).apply {
    background = UiCustomizer.instance.getTextOutputBackground(editor)
  }
  var progressBar: JProgressBar? = null
  if (progressStatus.progress != ProgressStatus.STOPPED_OK) {
    progressBar = JProgressBar(0, 100)
    progressBar.setUI(InlayProgressBarUI(progressStatus.progress))
    if (progressStatus.progress == ProgressStatus.RUNNING) {
      progressBar.isIndeterminate = true
      progressBar.foreground = ColorProgressBar.GREEN
    }
    else if (progressStatus.progress == ProgressStatus.STOPPED_ERROR) {
      progressBar.value = 100
    }
  }
  val label = JLabel(progressStatus.statusText)
  label.border = BorderFactory.createEmptyBorder(TEXT_BORDER, TEXT_BORDER, TEXT_BORDER, 0)
  progressBar?.let { progressPanel.add(it, BorderLayout.PAGE_START) }
  progressPanel.add(label, BorderLayout.CENTER)
  return progressPanel
}

class InlayProgressBarUI(private val status: ProgressStatus) : DarculaProgressBarUI() {
  override fun getFinishedColor(c: JComponent): Color {
    if (status == ProgressStatus.STOPPED_OK) {
      return JBColor.GRAY
    }
    else {
      return JBColor.RED
    }
  }
}