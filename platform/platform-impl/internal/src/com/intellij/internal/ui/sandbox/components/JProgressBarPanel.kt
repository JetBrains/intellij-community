// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.ProgressBarUtil
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import javax.swing.JComponent
import javax.swing.JProgressBar
import javax.swing.JSlider

internal class JProgressBarPanel : UISandboxPanel {

  override val title: String = "JProgressBar"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group("Determinate") {
        row {
          progressBar()
        }
        row("Passed:") {
          progressBar().applyToComponent {
            putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.PASSED_VALUE)
          }
        }
        row("Warning:") {
          progressBar().applyToComponent {
            putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.WARNING_VALUE)
          }
        }
        row("Failed:") {
          progressBar().applyToComponent {
            putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.FAILED_VALUE)
          }
        }
      }
      group("Indeterminate") {
        row {
          indProgressBar()
        }
        row("Passed:") {
          indProgressBar().applyToComponent {
            putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.PASSED_VALUE)
          }
        }
        row("Warning:") {
          indProgressBar().applyToComponent {
            putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.WARNING_VALUE)
          }
        }
        row("Failed:") {
          indProgressBar().applyToComponent {
            putClientProperty(ProgressBarUtil.STATUS_KEY, ProgressBarUtil.FAILED_VALUE)
          }
        }
      }
      group("Custom") {
        lateinit var progressBar: JProgressBar
        row {
          panel {
            row("Value:") {
              intTextField()
                .applyToComponent { text = "30" }
                .onChanged {
                  progressBar.value = it.text.toIntOrNull() ?: 30
                }
            }
            row {
              checkBox("Horizontal")
                .selected(true)
                .onChanged {
                  progressBar.orientation = if (it.isSelected) JProgressBar.HORIZONTAL else JProgressBar.VERTICAL
                }
            }
            row {
              checkBox("Indeterminate")
                .onChanged {
                  progressBar.isIndeterminate = it.isSelected
                }
            }
            row {
              lateinit var slider: JSlider

              fun setCustomPaint() {
                val quantityAndColors = listOf(slider.value to JBColor.GREEN, (100 - slider.value) to JBColor.BLUE)
                progressBar.putClientProperty(ProgressBarUtil.PROGRESS_PAINT_KEY,
                                              ProgressBarUtil.createMultiProgressPaint(quantityAndColors))
                progressBar.repaint()
              }

              val customPaint = checkBox("Custom progress paint")
                .onChanged {
                  if (it.isSelected) {
                    setCustomPaint()
                  }
                  else {
                    progressBar.putClientProperty(ProgressBarUtil.PROGRESS_PAINT_KEY, null)
                    progressBar.repaint()
                  }
                }.component
              slider = slider(0, 100, 5, 10)
                .applyToComponent { paintLabels = false }
                .onChanged {
                  setCustomPaint()
                }.enabledIf(customPaint.selected)
                .component
            }
          }

          progressBar = progressBar()
            .align(Align.FILL)
            .component
        }
      }
    }
  }

  private fun Row.progressBar(value: Int = 30, horizontal: Boolean = true): Cell<JProgressBar> {
    return cell(JProgressBar(if (horizontal) JProgressBar.HORIZONTAL else JProgressBar.VERTICAL)).applyToComponent {
      addChangeListener {
        toolTipText = "$value from $minimum..$maximum range"
      }

      setValue(value)
    }
  }

  private fun Row.indProgressBar(value: Int = 30, horizontal: Boolean = true): Cell<JProgressBar> {
    return progressBar(value, horizontal).applyToComponent {
      isIndeterminate = true
    }
  }
}