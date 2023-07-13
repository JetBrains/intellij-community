// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution.configuration.fragments

import com.intellij.execution.ui.*
import com.intellij.openapi.externalSystem.service.ui.util.LabeledSettingsFragmentInfo
import com.intellij.openapi.externalSystem.service.ui.util.SettingsFragmentInfo
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Ref
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.TextAccessor
import com.intellij.ui.components.JBTextField
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.text.JTextComponent


fun <S> SettingsEditorFragmentContainer<S>.addTag(
  id: String,
  @Nls name: String,
  @Nls group: String,
  @Nls hint: String?,
  getter: S.() -> Boolean,
  setter: S.(Boolean) -> Unit
): SettingsEditorFragment<S, TagButton> {
  val settingsEditorFragment = SettingsEditorFragment.createTag<S>(
    id,
    name,
    group,
    { it.getter() },
    { it, v -> it.setter(v) }
  )
  settingsEditorFragment.actionHint = hint
  return add(settingsEditorFragment)
}

@Suppress("HardCodedStringLiteral")
inline fun <S, reified V : Enum<V>> SettingsEditorFragmentContainer<S>.addVariantTag(
  id: String,
  @Nls(capitalization = Nls.Capitalization.Sentence) name: String,
  @Nls(capitalization = Nls.Capitalization.Title) group: String?,
  crossinline getter: S.() -> V,
  crossinline setter: S.(V) -> Unit,
  crossinline getText: (V) -> String
): VariantTagFragment<S, V> {
  val settingsEditorTag = VariantTagFragment.createFragment<S, V>(
    id,
    name,
    group,
    { EnumSet.allOf(V::class.java).toTypedArray() },
    { it.getter() },
    { it, v -> it.setter(v) },
    { it.getter() != EnumSet.allOf(V::class.java).first() }
  )
  settingsEditorTag.setVariantNameProvider { getText(it) }
  return add(settingsEditorTag)
}

inline fun <S, reified V : Enum<V>> SettingsEditorFragmentContainer<S>.addVariantFragment(
  info: LabeledSettingsFragmentInfo,
  crossinline getter: S.() -> V,
  crossinline setter: S.(V) -> Unit,
  crossinline getText: (V) -> @Nls String
): SettingsEditorFragment<S, SettingsEditorLabeledComponent<ComboBox<V>>> {
  val component = ComboBox(CollectionComboBoxModel(EnumSet.allOf(V::class.java).toList()))
  component.setRenderer(SimpleListCellRenderer.create("") { getText(it) })
  return addLabeledSettingsEditorFragment(
    component,
    info,
    { it, c -> c.selectedItem = it.getter() },
    { it, c -> it.setter(c.selectedItem!! as V) },
  )
}

fun <S, C> SettingsEditorFragmentContainer<S>.addRemovableLabeledTextSettingsEditorFragment(
  component: C,
  info: LabeledSettingsFragmentInfo,
  getter: S.() -> String?,
  setter: S.(String?) -> Unit,
  default: S.() -> String? = { null }
) where C : JComponent, C : TextAccessor = addRemovableLabeledSettingsEditorFragment(
  component,
  info,
  TextAccessor::getText,
  TextAccessor::setText,
  getter,
  setter,
  default
)

fun <S, C : JComponent, V> SettingsEditorFragmentContainer<S>.addRemovableLabeledSettingsEditorFragment(
  component: C,
  info: LabeledSettingsFragmentInfo,
  getterC: C.() -> V,
  setterC: C.(V) -> Unit,
  getterS: S.() -> V?,
  setterS: S.(V?) -> Unit,
  defaultS: S.() -> V? = { null }
): SettingsEditorFragment<S, SettingsEditorLabeledComponent<C>> {
  val ref = Ref<SettingsEditorFragment<S, SettingsEditorLabeledComponent<C>>>()
  val settingsEditorFragment = addLabeledSettingsEditorFragment(
    component,
    info,
    { it, c -> (it.getterS() ?: it.defaultS())?.let { c.setterC(it) } },
    { it, c -> it.setterS(if (ref.get().isSelected) c.getterC() else null) },
    { it.getterS() != null }
  )
  settingsEditorFragment.isRemovable = true
  ref.set(settingsEditorFragment)
  return settingsEditorFragment
}

fun <S, C : JComponent> SettingsEditorFragmentContainer<S>.addLabeledSettingsEditorFragment(
  component: C,
  settingsFragmentInfo: LabeledSettingsFragmentInfo,
  reset: (S, C) -> Unit,
  apply: (S, C) -> Unit,
) = addLabeledSettingsEditorFragment(component, settingsFragmentInfo, reset, apply) { true }
  .apply { isRemovable = false }

fun <S, C : JComponent> SettingsEditorFragmentContainer<S>.addSettingsEditorFragment(
  component: C,
  settingsFragmentInfo: SettingsFragmentInfo,
  reset: (S, C) -> Unit,
  apply: (S, C) -> Unit,
) = addSettingsEditorFragment(component, settingsFragmentInfo, reset, apply) { true }
  .apply { isRemovable = false }

fun <S, C : JComponent> SettingsEditorFragmentContainer<S>.addLabeledSettingsEditorFragment(
  component: C,
  info: LabeledSettingsFragmentInfo,
  reset: (S, C) -> Unit,
  apply: (S, C) -> Unit,
  initialSelection: (S) -> Boolean
) = addSettingsEditorFragment(
  SettingsEditorLabeledComponent(info.editorLabel, component),
  info,
  { it, c -> reset(it, c.component) },
  { it, c -> apply(it, c.component) },
  initialSelection
)

fun <S, C : JComponent> SettingsEditorFragmentContainer<S>.addSettingsEditorFragment(
  component: C,
  settingsFragmentInfo: SettingsFragmentInfo,
  reset: (S, C) -> Unit,
  apply: (S, C) -> Unit,
  initialSelection: (S) -> Boolean,
): SettingsEditorFragment<S, C> {
  val settingsEditorFragment = SettingsEditorFragment(
    settingsFragmentInfo.settingsId,
    settingsFragmentInfo.settingsName,
    settingsFragmentInfo.settingsGroup,
    component,
    settingsFragmentInfo.settingsPriority,
    settingsFragmentInfo.settingsType,
    reset,
    apply,
    initialSelection
  )

  settingsEditorFragment.setHint(settingsFragmentInfo.settingsHint)
  settingsEditorFragment.actionHint = settingsFragmentInfo.settingsActionHint

  val editorComponent = settingsEditorFragment.editorComponent
  if (settingsFragmentInfo.settingsType == SettingsEditorFragmentType.COMMAND_LINE) {
    if (editorComponent is JTextComponent ||
        editorComponent is JComboBox<*>) {
      CommonParameterFragments.setMonospaced(editorComponent)
    }
  }
  if (editorComponent is JBTextField) {
    FragmentedSettingsUtil.setupPlaceholderVisibility(editorComponent)
  }

  return add(settingsEditorFragment)
}
