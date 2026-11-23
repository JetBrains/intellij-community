// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsMutationBuilder
import com.intellij.platform.settings.v2.getValue
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.text.JTextComponent

/**
 * Shows the settings of the Presentation Assistant. Each input reads its value from the settings keys, and an Apply
 * writes the changed inputs in one batch. An input also follows a change of its keys from elsewhere while the page is
 * open, so the edits of the other inputs stay.
 */
internal class PresentationAssistantConfigurable: DslConfigurableBase(), Configurable {
  /** Holds the batch of the [apply] call that runs now, or `null` outside of it. The setters of the inputs fill it. */
  private var pendingChange: SettingsMutationBuilder? = null

  override fun getDisplayName(): String = IdeBundle.message("presentation.assistant.settings")

  override fun createPanel(): DialogPanel {
    return panel {
      row {
        checkBox(IdeBundle.message("presentation.assistant.configurable.toggle"))
          .bindSetting(key = PresentationAssistantSettings.SHOW_ACTION_DESCRIPTIONS,
                       bind = { bindSelected(it) },
                       show = AbstractButton::setSelected)
      }

      indent {
        panel {
          val comboGroup = "presentations_assistant_top_combo"

          if (PresentationAssistant.isThemeEnabled) {
            row(IdeBundle.message("presentation.assistant.configurable.theme")) {
              comboBox(CollectionComboBoxModel(PresentationAssistantTheme.entries.toMutableList(), PresentationAssistantSettings.popupTheme),
                       textListCellRenderer { it?.displayName })
                .widthGroup(comboGroup)
                .bindSetting(key = PresentationAssistantSettings.THEME,
                             bind = { bindItem(it.toNullableProperty(PresentationAssistantTheme.DEFAULT)) },
                             show = ComboBox<PresentationAssistantTheme>::setSelectedItem,
                             read = { PresentationAssistantSettings.popupTheme },
                             write = { set(PresentationAssistantSettings.THEME, it) })
            }
          }

          row(IdeBundle.message("presentation.assistant.configurable.popup.size")) {
            comboBox(CollectionComboBoxModel(PresentationAssistantPopupSize.entries.toMutableList(), PresentationAssistantSettings.POPUP_SIZE.getValue()),
                     textListCellRenderer { it?.displayName })
              .widthGroup(comboGroup)
              .bindSetting(key = PresentationAssistantSettings.POPUP_SIZE,
                           bind = { bindItem(it.toNullableProperty(PresentationAssistantPopupSize.MEDIUM)) },
                           show = ComboBox<PresentationAssistantPopupSize>::setSelectedItem)
          }

          row(IdeBundle.message("presentation.assistant.configurable.duration")) {
            intTextField()
              .bindSetting(key = PresentationAssistantSettings.POPUP_DURATION_MILLIS,
                           bind = { bindIntText(it) },
                           show = { text = it.toString() },
                           read = { PresentationAssistantSettings.POPUP_DURATION_MILLIS.getValue() / 1000 },
                           write = { set(PresentationAssistantSettings.POPUP_DURATION_MILLIS, it * 1000) })
              .gap(RightGap.SMALL)
            text(IdeBundle.message("presentation.assistant.configurable.duration.seconds"))
          }
          row(IdeBundle.message("presentation.assistant.configurable.popup.position")) {
            comboBox(CollectionComboBoxModel(PresentationAssistantPopupAlignment.entries.toMutableList(),
                                             PresentationAssistantSettings.popupAlignmentIfNoDelta),
                     textListCellRenderer { it?.displayName })
              .bindSetting(key = PresentationAssistantSettings.POPUP_ALIGNMENT,
                           bind = { bindItem(it) },
                           show = ComboBox<PresentationAssistantPopupAlignment>::setSelectedItem,
                           read = { PresentationAssistantSettings.popupAlignmentIfNoDelta },
                           write = {
                             set(PresentationAssistantSettings.POPUP_ALIGNMENT, it ?: PresentationAssistantPopupAlignment.defaultAlignment)
                             service<PresentationAssistant>().configuration.resetDelta()
                           })
          }
        }

        group(IdeBundle.message("presentation.assistant.configurable.keymap.group")) {
          row {
            val comboBox = comboBox(CollectionComboBoxModel(KeymapManagerEx.getInstanceEx().allKeymaps.toMutableList(),
                                                            PresentationAssistantSettings.MAIN_KEYMAP_NAME.getValue().toKeymap()),
                                    textListCellRenderer { it?.presentableName }).label(IdeBundle.message("presentation.assistant.configurable.keymap.main"))
              .bindSetting(key = PresentationAssistantSettings.MAIN_KEYMAP_NAME,
                           bind = { bindItem(it.toNullableProperty(KeymapKind.defaultForOS().value.toKeymap())) },
                           show = ComboBox<Keymap>::setSelectedItem,
                           read = { PresentationAssistantSettings.MAIN_KEYMAP_NAME.getValue().toKeymap() },
                           write = { set(PresentationAssistantSettings.MAIN_KEYMAP_NAME, it.name) })

            val label = textField()
              .label(IdeBundle.message("presentation.assistant.configurable.keymap.label"))
              .bindSetting(key = PresentationAssistantSettings.MAIN_KEYMAP_LABEL,
                           bind = { bindText(it) },
                           show = JTextComponent::setText)

            comboBox.onChanged {
              (it.selectedItem as? Keymap)?.let { keymap ->
                label.component.text = KeymapKind.from(keymap.name).defaultLabel
              }
            }
          }

          row {
            val showAlternativeProperty = AtomicBooleanProperty(PresentationAssistantSettings.SHOW_ALTERNATIVE_KEYMAP.getValue())

            checkBox(IdeBundle.message("presentation.assistant.configurable.keymap.additional"))
              .gap(RightGap.SMALL)
              .onChanged { showAlternativeProperty.set(it.isSelected) }
              .bindSetting(key = PresentationAssistantSettings.SHOW_ALTERNATIVE_KEYMAP,
                           bind = { bindSelected(it) },
                           show = AbstractButton::setSelected)

            val keymapKinds = mutableListOf<KeymapKind>()
            keymapKinds.addAll(KeymapManagerEx.getInstanceEx().allKeymaps.map { KeymapKind.from(it.name) })

            val defaultAlternativeKeymapKind = KeymapKind.defaultForOS().getAlternativeKind()
            if (!keymapKinds.contains(defaultAlternativeKeymapKind)) {
              keymapKinds.add(0, defaultAlternativeKeymapKind)
            }

            val alternativeKeymapKind = KeymapKind.from(PresentationAssistantSettings.ALTERNATIVE_KEYMAP_NAME.getValue())
            if (!keymapKinds.contains(alternativeKeymapKind)) {
              keymapKinds.add(0, alternativeKeymapKind)
            }

            val comboBox = comboBox(CollectionComboBoxModel(keymapKinds, alternativeKeymapKind), textListCellRenderer { it?.displayName })
              .bindSetting(key = PresentationAssistantSettings.ALTERNATIVE_KEYMAP_NAME,
                           bind = { bindItem(it.toNullableProperty(KeymapKind.defaultForOS().getAlternativeKind())) },
                           show = ComboBox<KeymapKind>::setSelectedItem,
                           read = { KeymapKind.from(PresentationAssistantSettings.ALTERNATIVE_KEYMAP_NAME.getValue()) },
                           write = { set(PresentationAssistantSettings.ALTERNATIVE_KEYMAP_NAME, it.value) })
              .enabledIf(showAlternativeProperty)

            val label = textField()
              .label(IdeBundle.message("presentation.assistant.configurable.keymap.label"))
              .bindSetting(key = PresentationAssistantSettings.ALTERNATIVE_KEYMAP_LABEL,
                           bind = { bindText(it) },
                           show = JTextComponent::setText)
              .enabledIf(showAlternativeProperty)

            comboBox.onChanged {
              (it.selectedItem as? KeymapKind)?.let { kind ->
                label.component.text = kind.defaultLabel
              }
            }
          }
        }
      }
    }
  }

  override fun apply() {
    val change = PresentationAssistantSettings.startChange()
    pendingChange = change
    try {
      super.apply()
    }
    finally {
      pendingChange = null
    }
    PresentationAssistantSettings.updateAsync { change.apply() }
  }

  /**
   * Binds this input to one setting of the Presentation Assistant. A reset shows the value that [read] returns. An
   * Apply adds [write] to the batch of the [apply] call only when the input holds another value than [read] returns,
   * so an untouched input writes nothing. A change of [key] shows the value of [read] again, also while the user edits
   * the page. It touches only this input, so the edits of the other inputs stay. The listener lives as long as the UI
   * of this configurable.
   *
   * @param key the key that [read] depends on
   * @param bind binds the input to a property, for example with [bindSelected] or [bindItem]
   * @param show puts a value into the input
   * @param read returns the value of the setting from the keys
   * @param write adds the writes of a changed value to the batch
   * @return this cell
   */
  private fun <C : JComponent, V> Cell<C>.bindSetting(
    key: SettingsKey<*>,
    bind: Cell<C>.(MutableProperty<V>) -> Cell<C>,
    show: C.(V) -> Unit,
    read: () -> V,
    write: SettingsMutationBuilder.(V) -> Unit,
  ): Cell<C> {
    val uiDisposable = checkNotNull(disposable) { "The UI of the configurable is not created" }
    // The vault calls a listener on the thread of the write, so the input is updated on the EDT. The Settings dialog
    // is modal, so the update must run in any modality.
    key.addListener(uiDisposable, SettingsContext.app()) { _, _ ->
      ApplicationManager.getApplication().invokeLater({ component.show(read()) }, ModalityState.any())
    }
    return bind(MutableProperty(read) { value ->
      if (value != read()) {
        checkNotNull(pendingChange) { "An input is applied outside of apply()" }.write(value)
      }
    })
  }

  /**
   * Binds this input to [key], for an input that holds the value of the key with no conversion. The other
   * [bindSetting] does the work.
   *
   * @param key the key that the input shows and writes
   * @param bind binds the input to a property, for example with [bindSelected] or [bindItem]
   * @param show puts a value into the input
   * @return this cell
   */
  private fun <C : JComponent, V> Cell<C>.bindSetting(
    key: SettingsKey<V>,
    bind: Cell<C>.(MutableProperty<V>) -> Cell<C>,
    show: C.(V) -> Unit,
  ): Cell<C> = bindSetting(key, bind, show, read = { key.getValue() }, write = { set(key, it) })
}

private fun String.toKeymap(): Keymap = KeymapManagerEx.getInstanceEx().let { it.getKeymap(this) ?: it.allKeymaps.first() }
