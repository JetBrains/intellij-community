// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation.settings

import com.intellij.execution.process.elevation.ElevationBundle
import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import kotlin.time.ExperimentalTime
import kotlin.time.hours
import kotlin.time.minutes

class ElevationSettingsConfigurable : BoundConfigurable(ElevationBundle.message("elevation.settings.configurable")),
                                      Configurable.NoScroll {
  val settings = ElevationSettings.getInstance()

  override fun createPanel(): DialogPanel = panel {
    row {
      val firstSentence =
        if (SystemInfo.isUnix) ElevationBundle.message("text.running.privileged.processes.requires.sudo.authorization")
        else ElevationBundle.message("text.running.privileged.processes.requires.uac.authorization")
      JLabel().apply { // UI DSL label() doesn't support HTML
        text = ExplanatoryTextUiUtil.message(firstSentence, fontMetrics = getFontMetrics(font))
      }.invoke(grow)
    }

    row {
      lateinit var keepAuth: CellBuilder<JBCheckBox>
      cell {
        keepAuth = checkBox(if (SystemInfo.isUnix) ElevationBundle.message("checkbox.keep.sudo.authorization.for")
                            else ElevationBundle.message("checkbox.keep.uac.authorization.for"),
                            settings::isKeepAuth)

        comboBox(CollectionComboBoxModel(getTimeLimitItems(settings.quotaTimeLimitMs)),
                 settings::quotaTimeLimitMs, DurationListCellRenderer())
          .enableIf(keepAuth.selected)
      }
      row {
        checkBox(ElevationBundle.message("checkbox.refresh.time.limit.after.each.process"),
                 settings::isRefreshable)
          .enableIf(keepAuth.selected)
          .comment(ElevationBundle.message("text.elevation.settings.refresh.quota.explanatory.comment"))
      }
    }
  }

  @Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
  @OptIn(ExperimentalTime::class)
  private fun getTimeLimitItems(currentDurationMs: Long = 0): List<Long> {
    return mutableListOf(
      5.minutes,
      15.minutes,
      30.minutes,
      1.hours,
      3.hours,
      10.hours,
    ).map {
      it.toLongMilliseconds()
    }.toMutableList().apply {
      if (currentDurationMs > 0 && currentDurationMs !in this) {
        add(currentDurationMs)
      }
    }
  }

  private class DurationListCellRenderer : ListCellRenderer<Long?> {
    private val delegate = DefaultListCellRenderer()

    override fun getListCellRendererComponent(list: JList<out Long?>?,
                                              value: Long?,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component? {
      val prettyValue = value?.let { durationMs ->
        NlsMessages.formatDuration(durationMs)
      }
      return delegate.getListCellRendererComponent(list, prettyValue, index, isSelected, cellHasFocus)
    }
  }
}