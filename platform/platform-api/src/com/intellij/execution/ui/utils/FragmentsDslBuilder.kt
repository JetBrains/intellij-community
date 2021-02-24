// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui.utils

import com.intellij.execution.ui.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.concurrent.thread

@DslMarker
annotation class FragmentsDsl

abstract class AbstractFragmentBuilder<Settings : FragmentedSettings> {
  @Nls
  var actionHint: String? = null

  @Nls
  var group: String? = null

  @Nls
  var actionDescription: String? = null

  abstract fun build(): SettingsEditorFragment<Settings, *>
}

@ApiStatus.Experimental
@FragmentsDsl
class Group<Settings : FragmentedSettings>(
  val id: String,
  val name: @Nls String
) : AbstractFragmentBuilder<Settings>() {

  var applyVisibility: ((Settings, Boolean) -> Unit)? = null

  var visible: (Settings) -> Boolean = { true }

  @Nls
  var childrenGroupName: String? = null

  var children: FragmentsBuilder<Settings>.() -> Unit = {}

  override fun build(): NestedGroupFragment<Settings> {
    return object : NestedGroupFragment<Settings>(id, name, group, visible) {
      override fun createChildren(): MutableList<SettingsEditorFragment<Settings, *>> {
        return FragmentsBuilder<Settings>().also(this@Group.children).build()
      }

      override fun getChildrenGroupName(): String? = this@Group.childrenGroupName ?: super.getChildrenGroupName()

      override fun applyEditorTo(s: Settings) {
        applyVisibility?.let { it(s, component().isVisible) }
        super.applyEditorTo(s)
      }

      override fun isInitiallyVisible(s: Settings): Boolean {
        val serializableVisibility = applyVisibility != null
        return if (serializableVisibility) {
          visible(s)
        }
        else {
          super.isInitiallyVisible(s)
        }
      }
    }.also {
      it.actionHint = actionHint
      it.actionDescription = actionDescription
    }
  }
}

@ApiStatus.Experimental
@FragmentsDsl
class Tag<Settings : FragmentedSettings>(
  val id: String,
  val name: @Nls String
) : AbstractFragmentBuilder<Settings>() {

  var holder: SettingsEditorFragment<Settings, *>? = null

  var buttonAction: (SettingsEditorFragment<Settings, *>) -> Unit = { it.isSelected = false }

  var getter: (Settings) -> Boolean = { false }
  var setter: (Settings, Boolean) -> Unit = { _, _ -> }

  @Nls
  var toolTip: String? = null

  var validation: ((Settings, TagButton) -> ValidationInfo?)? = null

  override fun build(): SettingsEditorFragment<Settings, TagButton> {
    val ref = Ref<SettingsEditorFragment<Settings, *>>()
    val tagButton = TagButton(name) {
      buttonAction(ref.get())
    }

    return Fragment<Settings, TagButton>(id, tagButton).also {
      it.name = name
      it.actionHint = actionHint
      it.group = group
      it.visible = getter
      it.apply = { s, c -> setter(s, c.isVisible) }
      it.reset = { s, c -> c.isVisible = getter(s) }
      it.validation = validation
      it.actionDescription = actionDescription
    }.build().also {
      it.component().setToolTip(toolTip ?: actionHint)
      if (holder == null) {
        ref.set(it)
      }
      else {
        ref.set(holder)
      }
    }
  }
}

@ApiStatus.Experimental
@FragmentsDsl
class Fragment<Settings : FragmentedSettings, Component : JComponent>(
  val id: String,
  private val component: Component
) : AbstractFragmentBuilder<Settings>() {

  @Nls
  var name: String? = null

  var visible: (Settings) -> Boolean = { true }

  var reset: (Settings, Component) -> Unit = { _, _ -> }
  var apply: (Settings, Component) -> Unit = { _, _ -> }

  var isRemovable: Boolean = true

  var validation: ((Settings, Component) -> ValidationInfo?)? = null

  @Nls
  var hint: String? = null

  var commandLinePosition: Int = 0

  override fun build(): SettingsEditorFragment<Settings, Component> {
    return object : SettingsEditorFragment<Settings, Component>(id, name, group, component, commandLinePosition, reset, apply, visible) {

      private val validator = if (validation != null) ComponentValidator(this) else null

      override fun applyEditorTo(s: Settings) {
        super.applyEditorTo(s)

        thread {
          if (validator != null) {
            val validationInfo = (validation!!)(s, this.component())

            validationInfo?.component?.let {
              if (ComponentValidator.getInstance(it).isEmpty) {
                when (it) {
                  is ComponentWithBrowseButton<*> -> validator.withOutlineProvider(ComponentValidator.CWBB_PROVIDER)
                  is TagButton -> validator.withOutlineProvider(TagButton.COMPONENT_VALIDATOR_TAG_PROVIDER)
                }

                validator.installOn(it)
              }
            }

            UIUtil.invokeLaterIfNeeded { validator.updateInfo(validationInfo) }
          }
        }
      }

      override fun isTag(): Boolean = component is TagButton
    }.also {
      it.isRemovable = isRemovable
      it.setHint(hint)

      it.actionDescription = actionDescription
      it.actionHint = actionHint

      if (component is Disposable) {
        Disposer.register(it, component)
      }
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

  fun <Builder : AbstractFragmentBuilder<Settings>> withCustomBuilder(
    builder: Builder,
    setup: Builder.() -> Unit
  ): SettingsEditorFragment<Settings, *> {
    return builder.apply(setup).let { it.build().apply { fragments += this } }
  }

  fun <Component : JComponent> fragment(
    id: String,
    component: Component,
    setup: Fragment<Settings, Component>.() -> Unit
  ): SettingsEditorFragment<Settings, Component> {
    return Fragment<Settings, Component>(id, component).also(setup).let { it.build().apply { fragments += this } }
  }

  fun customFragment(fragment: SettingsEditorFragment<Settings, *>) = fragment.apply { fragments += this }

  fun tag(id: String, @Nls name: String, setup: Tag<Settings>.() -> Unit): SettingsEditorFragment<Settings, TagButton> {
    return Tag<Settings>(id, name).also(setup).let { it.build().apply { fragments += this } }
  }

  fun group(id: String, @Nls name: String, setup: Group<Settings>.() -> Unit): NestedGroupFragment<Settings> {
    return Group<Settings>(id, name).also(setup).let { it.build().apply { fragments += this } }
  }

  fun build() = fragments.toMutableList()
}

@ApiStatus.Experimental
inline fun <Settings : FragmentedSettings> fragments(
  @Nls title: String? = null,
  setup: FragmentsBuilder<Settings>.() -> Unit
): MutableList<SettingsEditorFragment<Settings, *>> = FragmentsBuilder<Settings>().apply {
  if (title != null) {
    fragment("title", JLabel(title).also { it.font = JBUI.Fonts.label().deriveFont(Font.BOLD) }) {
      isRemovable = false
      commandLinePosition = -1
    }
  }
}.also(setup).build()