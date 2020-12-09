// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui.utils

import com.intellij.execution.ui.FragmentedSettings
import com.intellij.execution.ui.NestedGroupFragment
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.execution.ui.TagButton
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

@DslMarker
private annotation class FragmentsDsl

@ApiStatus.Experimental
@FragmentsDsl
class Group<Settings : FragmentedSettings>(val id: String) {
  @Nls
  var name: String? = null

  @Nls
  var group: String? = null

  @Nls
  var childrenGroupName: String? = null

  var children: FragmentsBuilder<Settings>.() -> Unit = {}
  var visible: (Settings) -> Boolean = { true }

  fun build(): NestedGroupFragment<Settings> {
    return object : NestedGroupFragment<Settings>(id, name, group, visible) {
      override fun createChildren(): MutableList<SettingsEditorFragment<Settings, *>> {
        return FragmentsBuilder<Settings>().also(this@Group.children).build()
      }

      override fun getChildrenGroupName(): String? = this@Group.childrenGroupName ?: super.getChildrenGroupName()
    }
  }
}

@ApiStatus.Experimental
@FragmentsDsl
class Fragment<Settings : FragmentedSettings, Component : JComponent>(val id: String, private val component: Component) {
  var reset: (Settings, Component) -> Unit = { _, _ -> }
  var apply: (Settings, Component) -> Unit = { _, _ -> }
  var visible: (Settings) -> Boolean = { true }
  var isRemovable: Boolean = true

  @Nls(capitalization = Nls.Capitalization.Sentence)
  var name: String? = null

  @Nls(capitalization = Nls.Capitalization.Title)
  var group: String? = null

  @Nls
  var hint: String? = null

  @Nls
  var actionHint: String? = null

  var commandLinePosition: Int = 0

  fun build(): SettingsEditorFragment<Settings, Component> {
    return SettingsEditorFragment(id, name, group, component, commandLinePosition, reset, apply, visible).also {
      it.isRemovable = isRemovable
      it.setHint(hint)
      it.actionHint = actionHint
    }
  }
}

@ApiStatus.Experimental
@FragmentsDsl
class FragmentsBuilder<Settings : FragmentedSettings> {
  private val fragments = arrayListOf<SettingsEditorFragment<Settings, *>>()

  fun <Component : JComponent> Component.asFragment(
    id: String,
    setup: Fragment<Settings, Component>.() -> Unit
  ): SettingsEditorFragment<Settings, Component> = fragment(id, this, setup)

  fun <Component : JComponent> fragment(
    id: String,
    component: Component,
    setup: Fragment<Settings, Component>.() -> Unit
  ): SettingsEditorFragment<Settings, Component> {
    return Fragment<Settings, Component>(id, component).also(setup).let { it.build().apply { fragments += this } }
  }

  fun tag(
    id: String,
    @Nls name: String,
    getter: (Settings) -> Boolean,
    setter: (Settings, Boolean) -> Unit,
    @Nls group: String? = null,
    @Nls actionHint: String? = null,
    @Nls toolTip: String? = null
  ): SettingsEditorFragment<Settings, TagButton> {
    return SettingsEditorFragment.createTag(id, name, group, getter, setter).also {
      it.actionHint = actionHint
      it.component().setToolTip(toolTip)
    }.apply { fragments += this }
  }

  fun group(id: String, setup: Group<Settings>.() -> Unit): NestedGroupFragment<Settings> {
    return Group<Settings>(id).also(setup).let { it.build().apply { fragments += this } }
  }

  fun build() = fragments.toMutableList()
}

@ApiStatus.Experimental
inline fun <Settings : FragmentedSettings> fragments(
  setup: FragmentsBuilder<Settings>.() -> Unit
): MutableList<SettingsEditorFragment<Settings, *>> = FragmentsBuilder<Settings>().also(setup).build()