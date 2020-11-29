// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui.utils

import com.intellij.execution.ui.FragmentedSettings
import com.intellij.execution.ui.NestedGroupFragment
import com.intellij.execution.ui.SettingsEditorFragment
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

@DslMarker private annotation class FragmentsDsl

@ApiStatus.Experimental
@FragmentsDsl class Group<Settings : FragmentedSettings> {
  lateinit var id: String
  var visible: (Settings) -> Boolean = { true }
  @Nls var name: String? = null
  @Nls var group: String? = null
  @Nls var childrenGroupName: String? = null
  var children: FragmentsBuilder<Settings>.() -> Unit = {}

  fun build(): NestedGroupFragment<Settings> {
    return object : NestedGroupFragment<Settings>(id, name, group, visible) {
      override fun createChildren(): MutableList<SettingsEditorFragment<Settings, *>>  {
        return FragmentsBuilder<Settings>().also(this@Group.children).fragments
      }

      override fun getChildrenGroupName(): String? {
        return this@Group.childrenGroupName ?: super.getChildrenGroupName()
      }
    }
  }
}

@ApiStatus.Experimental
@FragmentsDsl class Tag<Settings : FragmentedSettings> {
  lateinit var id: String
  @Nls lateinit var name: String
  lateinit var getter: (Settings) -> Boolean
  lateinit var setter: (Settings, Boolean) -> Unit
  @Nls var group: String? = null

  fun build(): SettingsEditorFragment<Settings, *> {
    return SettingsEditorFragment.createTag(id, name, group, getter, setter)
  }
}

@ApiStatus.Experimental
@FragmentsDsl class Fragment<Settings : FragmentedSettings, Component : JComponent>(private val component: Component) {
  lateinit var id: String
  lateinit var reset: (Settings, Component) -> Unit
  lateinit var apply: (Settings, Component) -> Unit
  var visible: (Settings) -> Boolean = { true }
  var isRemovable: Boolean = true
  @Nls(capitalization = Nls.Capitalization.Sentence) var name: String? = null
  @Nls(capitalization = Nls.Capitalization.Title) var group: String? = null
  @Nls var hint: String? = null
  var commandLinePosition: Int = 0

  fun build(): SettingsEditorFragment<Settings, Component> {
    return SettingsEditorFragment(id, name, group, component, commandLinePosition, reset, apply, visible).also {
      it.isRemovable = isRemovable
      it.setHint(hint)
    }
  }
}

@ApiStatus.Experimental
@FragmentsDsl class FragmentsBuilder<Settings : FragmentedSettings> {
  val fragments = arrayListOf<SettingsEditorFragment<Settings, *>>()

  infix fun <Component : JComponent> Component.asFragment(
    setup: Fragment<Settings, Component>.() -> Unit
  ): SettingsEditorFragment<Settings, Component> = fragment(this, setup)

  fun <Component : JComponent> fragment(
    component: Component,
    setup: Fragment<Settings, Component>.() -> Unit
  ): SettingsEditorFragment<Settings, Component> {
    return Fragment<Settings, Component>(component).also { it.setup() }.let { it.build().apply { fragments += this } }
  }

  fun tag(setup: Tag<Settings>.() -> Unit): SettingsEditorFragment<Settings, *> {
    return Tag<Settings>().also { it.setup() }.let { it.build().apply { fragments += this } }
  }

  fun group(setup: Group<Settings>.() -> Unit): NestedGroupFragment<Settings> {
    return Group<Settings>().also { it.setup() }.let { it.build().apply { fragments += this } }
  }
}

@ApiStatus.Experimental
fun <Settings : FragmentedSettings> fragments(
  setup: FragmentsBuilder<Settings>.() -> Unit
): MutableList<SettingsEditorFragment<Settings, *>> {
  return FragmentsBuilder<Settings>().also { it.setup() }.fragments
}