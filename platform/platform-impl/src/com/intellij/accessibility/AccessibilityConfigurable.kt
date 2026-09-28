// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.actions.IdeScaleTransformer
import com.intellij.ide.isSupportScreenReadersOverridden
import com.intellij.ide.soundSignals.soundSignalsGroup
import com.intellij.ide.ui.ColorBlindness
import com.intellij.ide.ui.ColorBlindnessSupport
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.ui.logIdeZoomChanged
import com.intellij.ide.ui.percentStringValue
import com.intellij.ide.ui.percentValue
import com.intellij.notification.NotificationAnnouncingMode
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.notification.impl.isNotificationAnnouncerFeatureAvailable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.PlatformEditorBundle
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.BackedByPersistentState
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.UIBundle
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.and
import com.intellij.util.ui.RestartDialogImpl
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

internal class AccessibilityConfigurable : BoundSearchableConfigurable(
  message("configurable.AccessibilityConfigurable.display.name"), "preferences.lookFeel", "preferences.accessibility",
), BackedByPersistentState {
  override fun getBackingComponents(): Collection<PersistentStateComponent<*>> =
    listOf(UISettings.getInstance(), GeneralSettings.getInstance(), service<AccessibilitySettings>(),
           NotificationsConfigurationImpl.getInstanceImpl())

  override fun createPanel(): DialogPanel = panel {
    visionGroup()
    screenReaderModeGroup()
    soundSignalsGroup()
  }

  override fun apply() {
    val uiSettings = UISettings.getInstance()
    val generalSettings = GeneralSettings.getInstance()
    val oldUiSettingsModificationCount = uiSettings.state.modificationCount
    val oldIsSupportScreenReaders = generalSettings.isSupportScreenReaders

    super.apply()

    if (uiSettings.state.modificationCount != oldUiSettingsModificationCount) {
      uiSettings.fireUISettingsChanged()
      EditorFactory.getInstance().refreshAllEditors()
    }
    if (oldIsSupportScreenReaders != generalSettings.isSupportScreenReaders) {
      ApplicationManager.getApplication().invokeLater { RestartDialogImpl.showRestartRequired() }
    }
  }

  private fun Panel.visionGroup() {
    val settings = UISettings.getInstance()
    group(message("accessibility.group.vision")) {
      row(message("combobox.ide.scale.percent")) {
        val defaultScale = UISettingsUtils.defaultScale(false)
        lateinit var resetZoom: Cell<ActionLink>

        val model = IdeScaleTransformer.Settings.createIdeScaleComboboxModel()
        comboBox(model)
          .bindItem({ settings.ideScale.percentStringValue }, { })
          .onChanged {
            if (IdeScaleTransformer.Settings.validatePercentScaleInput(it.item, false) != null) return@onChanged

            IdeScaleTransformer.Settings.scaleFromPercentStringValue(it.item, false)?.let { scale ->
              logIdeZoomChanged(scale, false)
              resetZoom.visible(scale.percentValue != defaultScale.percentValue)
              settings.ideScale = scale
              invokeLater {
                // Invoke later to avoid NPE in JComboBox.repaint()
                settings.fireUISettingsChanged()
              }
            }
          }
          .applyToComponent {
            isEditable = true
          }
          .commentRight(getScaleComment())
          .validationOnInput {
            IdeScaleTransformer.Settings.validatePercentScaleInput(this, it, false)
          }
          .gap(RightGap.SMALL)

        resetZoom = link(message("ide.scale.reset.link")) {
          model.selectedItem = defaultScale.percentStringValue
        }.apply { visible(settings.ideScale.percentValue != defaultScale.percentValue) }
      }

      row {
        checkBox(CheckboxDescriptor(message("checkbox.accessibility.contrast.scrollbars"), settings::useContrastScrollbars))
      }

      val supportedValues = ColorBlindness.entries.filter { ColorBlindnessSupport.get(it) != null }
      if (supportedValues.isNotEmpty()) {
        val colorBlindnessProperty = MutableProperty({ settings.colorBlindness }, { settings.colorBlindness = it })
        val onApply = {
          // callback executed not when all changes are applied, but one component by one, so, reload later when everything was applied
          ApplicationManager.getApplication().invokeLater(Runnable {
            DefaultColorSchemesManager.getInstance().reload()
            (EditorColorsManager.getInstance() as EditorColorsManagerImpl).schemeChangedOrSwitched(null)
          })
        }

        row {
          if (supportedValues.size == 1) {
            checkBox(UIBundle.message("color.blindness.checkbox.text"))
              .comment(UIBundle.message("color.blindness.checkbox.comment"))
              .bind({ if (it.isSelected) supportedValues.first() else null },
                    { it, value -> it.isSelected = value != null },
                    colorBlindnessProperty)
              .onApply(onApply)
          }
          else {
            val enableColorBlindness = checkBox(UIBundle.message("color.blindness.combobox.text"))
              .selected(colorBlindnessProperty.get() != null)
            comboBox(supportedValues, renderer = textListCellRenderer("") { PlatformEditorBundle.message(it.key) })
              .enabledIf(enableColorBlindness.selected)
              .comment(UIBundle.message("color.blindness.combobox.comment"))
              .bind({ if (enableColorBlindness.component.isSelected) it.selectedItem as? ColorBlindness else null },
                    { it, value -> it.selectedItem = value ?: supportedValues.first() },
                    colorBlindnessProperty)
              .onApply(onApply)
              .accessibleName(UIBundle.message("color.blindness.checkbox.text"))
          }

          link(UIBundle.message("color.blindness.link.to.help")) {
            HelpManager.getInstance().invokeHelp("Colorblind_Settings")
          }.applyToComponent {
            setExternalLinkIcon()
          }
        }
      }
    }
  }

  private fun Panel.screenReaderModeGroup() {
    val generalSettings = GeneralSettings.getInstance()
    lateinit var screenReaderCell: Cell<JBCheckBox>
    group(message("accessibility.group.screen.reader.mode")) {
      row {
        val isOverridden = isSupportScreenReadersOverridden()
        val ctrlTab = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK))
        val ctrlShiftTab = KeymapUtil.getKeystrokeText(
          KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK))
        screenReaderCell = checkBox(message("checkbox.support.screen.readers"))
          .bindSelected(generalSettings::isSupportScreenReaders) { generalSettings.isSupportScreenReaders = it }
          .comment(message("support.screen.readers.tab", ctrlTab, ctrlShiftTab))
          .commentRight(if (isOverridden) message("overridden.by.jvm.property", GeneralSettings.SUPPORT_SCREEN_READERS)
                        else message("ide.restart.required.comment"))
          .enabled(!isOverridden)
      }
      if (isNotificationAnnouncerFeatureAvailable) {
        indent { notificationAnnouncingRows(screenReaderCell.selected) }
      }
    }
  }

  private fun Panel.notificationAnnouncingRows(screenReaderSelected: ComponentPredicate) {
    val settings = NotificationsConfigurationImpl.getInstanceImpl()
    lateinit var announce: Cell<JBCheckBox>
    lateinit var combo: Cell<ComboBox<NotificationAnnouncingMode>>
    row {
      announce = checkBox(message("notifications.configurable.announcing.checkbox"))
        .bindSelected({ settings.notificationAnnouncingMode != NotificationAnnouncingMode.NONE },
                      { settings.notificationAnnouncingMode = if (it) combo.component.item else NotificationAnnouncingMode.NONE })
        .enabledIf(screenReaderSelected)
    }
    indent {
      row(message("notifications.configurable.announcing.title")) {
        val options = listOf(NotificationAnnouncingMode.MEDIUM, NotificationAnnouncingMode.HIGH)
        combo = comboBox(options, textListCellRenderer("") { announcingModeTitle(it) })
          .bindItem({ settings.notificationAnnouncingMode.takeIf { it != NotificationAnnouncingMode.NONE } ?: NotificationAnnouncingMode.MEDIUM },
                    { if (announce.component.isSelected) settings.notificationAnnouncingMode = it!! })
        if (SystemInfoRt.isMac) combo.comment(message("notifications.configurable.announcing.comment"))
      }.enabledIf(screenReaderSelected and announce.selected)
    }
  }
}

private fun announcingModeTitle(mode: NotificationAnnouncingMode): @Nls String {
  val high = mode == NotificationAnnouncingMode.HIGH
  return message(when {
    SystemInfoRt.isMac -> if (high) "notifications.configurable.announcing.value.high" else "notifications.configurable.announcing.value.medium"
    else -> if (high) "notifications.configurable.announcing.value.interrupting" else "notifications.configurable.announcing.value.not.interrupting"
  })
}

private fun getScaleComment(): @Nls String? {
  val zoomInString = KeymapUtil.getShortcutTextOrNull("ZoomInIdeAction")
  val zoomOutString = KeymapUtil.getShortcutTextOrNull("ZoomOutIdeAction")
  val resetScaleString = KeymapUtil.getShortcutTextOrNull("ResetIdeScaleAction")

  if (zoomInString != null && zoomOutString != null && resetScaleString != null) {
    return message("combobox.ide.scale.comment.format", zoomInString, zoomOutString, resetScaleString)
  }

  return null
}
