// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.elevation.settings

import com.intellij.execution.process.elevation.ElevationBundle
import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.ListCellRenderer
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ElevationSettingsConfigurable : BoundConfigurable(ElevationBundle.message("elevation.settings.configurable")),
                                      Configurable.NoScroll {
  val settings = ElevationSettings.getInstance()

  override fun createPanel(): DialogPanel {
    return panel {
      row {
        val firstSentence = if (SystemInfo.isUnix) ElevationBundle.message("text.running.privileged.processes.requires.sudo.authorization")
        else ElevationBundle.message("text.running.privileged.processes.requires.uac.authorization")
        val productName = ApplicationNamesInfo.getInstance().fullProductName
        text("$firstSentence<br><br>${ElevationBundle.message("text.elevation.explanatory.comment.html", productName)}<br><br>${
          ElevationBundle.message("text.elevation.explanatory.warning.html")}", maxLineLength = DEFAULT_COMMENT_WIDTH)
      }

      lateinit var keepAuth: Cell<JBCheckBox>
      row {
        keepAuth = checkBox(if (SystemInfo.isUnix) ElevationBundle.message("checkbox.keep.sudo.authorization.for")
                            else ElevationBundle.message("checkbox.keep.uac.authorization.for"))
          .bindSelected(settings::isKeepAuth)
          .gap(RightGap.SMALL)

        comboBox(CollectionComboBoxModel(getTimeLimitItems(settings.quotaTimeLimitMs)), DurationListCellRenderer())
          .bindItem(settings::quotaTimeLimitMs.toNullableProperty())
          .enabledIf(keepAuth.selected)
      }

      indent {
        row {
          checkBox(ElevationBundle.message("checkbox.refresh.time.limit.after.each.process"))
            .bindSelected(settings::isRefreshable)
            .enabledIf(keepAuth.selected)
            .comment(ElevationBundle.message("text.elevation.settings.refresh.quota.explanatory.comment"))
        }
      }
    }
  }

  private fun getTimeLimitItems(currentDurationMs: Long = 0): List<Long> {
    return mutableListOf(
      5.minutes,
      15.minutes,
      30.minutes,
      1.hours,
      3.hours,
      10.hours,
    ).map {
      it.inWholeMilliseconds
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