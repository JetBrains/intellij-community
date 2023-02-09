// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui.utils

import com.intellij.execution.ui.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
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
  val parentId: String,
  val id: String,
  @Nls val name: String,
  private val extenders: List<FragmentsDslBuilderExtender<Settings>>
) : AbstractFragmentBuilder<Settings>() {

  var applyVisibility: ((Settings, Boolean) -> Unit)? = null

  var visible: (Settings) -> Boolean = { true }

  @Nls
  var childrenGroupName: String? = null

  var children: FragmentsBuilder<Settings>.() -> Unit = {}

  override fun build(): NestedGroupFragment<Settings> {
    return object : NestedGroupFragment<Settings>(id, name, group, visible) {
      override fun createChildren(): MutableList<SettingsEditorFragment<Settings, *>> {
        return FragmentsBuilder(parentId, this@Group.id, extenders).also(this@Group.children).build()
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
class VariantableTag<Settings : FragmentedSettings, V : Any>(
  val id: String,
  @Nls val name: String,
) : AbstractFragmentBuilder<Settings>() {
  private data class Variant<S, V>(
    val key: V,
    @Nls val name: String,
    @Nls val hint: String?,
    @Nls val description: String?,
    val getter: (S) -> Boolean,
    val setter: (S, Boolean) -> Unit,
    val validation: (S) -> ValidationInfo?
  )

  private val myVariants = mutableMapOf<V, Variant<Settings, V>>()

  var visible: (Settings) -> Boolean = { false }

  fun variant(
    key: V,
    @Nls name: String,
    @Nls hint: String? = null,
    @Nls description: String? = null,
    getter: (Settings) -> Boolean,
    setter: (Settings, Boolean) -> Unit = { _, _ -> },
    validation: (Settings) -> ValidationInfo? = { null }
  ) {
    myVariants[key] = Variant(key, name, hint, description, getter, setter, validation)
  }

  override fun build(): SettingsEditorFragment<Settings, TagButton> {
    val getter: (Settings) -> V = { s -> myVariants.values.first { it.getter(s) }.key }
    val setter: (Settings, V?) -> Unit = { s, v -> myVariants.forEach { e -> e.value.setter(s, v == e.key) } }
    val array = Array<Any>(myVariants.size) { myVariants.keys.elementAt(it) }

    return VariantTagFragment.createFragment(id, name, group, {
      @Suppress("UNCHECKED_CAST")
      array as Array<V>
    }, getter, setter, visible).also {
      it.setValidation { settings ->
        val result = myVariants[it.selectedVariant]?.validation?.invoke(settings) ?: return@setValidation listOf(
          ValidationInfo("").forComponent(it.editorComponent)
        )

        listOf(result.forComponent(it.editorComponent))
      }
      it.setVariantNameProvider { v -> myVariants[v]?.name }
      it.setVariantHintProvider { v -> myVariants[v]?.hint }
      it.setVariantDescriptionProvider { v -> myVariants[v]?.description }
    }
  }
}

@ApiStatus.Experimental
@FragmentsDsl
class Tag<Settings : FragmentedSettings>(
  val id: String,
  @Nls val name: String
) : AbstractFragmentBuilder<Settings>() {
  var getter: (Settings) -> Boolean = { false }
  var setter: (Settings, Boolean) -> Unit = { _, _ -> }

  @Nls
  var toolTip: String? = null

  var validation: ((Settings, TagButton) -> ValidationInfo?)? = null

  override fun build(): SettingsEditorFragment<Settings, TagButton> {
    val ref = Ref<SettingsEditorFragment<Settings, *>>()
    val tagButton = TagButton(name) {
      ref.get().isSelected = false
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
      ref.set(it)
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

  var onToggle: (Boolean) -> Unit = {}

  @Nls
  var hint: String? = null

  var commandLinePosition: Int = 0

  var editorGetter: ((Component) -> JComponent)? = null

  override fun build(): SettingsEditorFragment<Settings, Component> {
    return object : SettingsEditorFragment<Settings, Component>(id, name, group, component, commandLinePosition, reset, apply, visible) {

      init {
        setEditorGetter(editorGetter)
      }

      private val validator = if (validation != null) ComponentValidator(this) else null

      override fun validate(s: Settings) {
        ApplicationManager.getApplication().executeOnPooledThread {
          if (validator != null) {
            val validationInfo = (validation!!)(s, this.component())?.setComponentIfNeeded()

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

      override fun toggle(selected: Boolean, e: AnActionEvent?) {
        onToggle(selected)
        super.toggle(selected, e)
      }

      private fun ValidationInfo.setComponentIfNeeded(): ValidationInfo {
        return if (component == null) forComponent(editorComponent) else this
      }
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
class FragmentsBuilder<Settings : FragmentedSettings>(
  parentId: String?,
  id: String,
  private val extenders: List<FragmentsDslBuilderExtender<Settings>>
) {
  val fullId = (if (parentId == null) "" else "$parentId.") + id

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

  fun <V : Any> variantableTag(id: String, @Nls name: String, setup: VariantableTag<Settings, V>.() -> Unit) {
    return VariantableTag<Settings, V>(id, name).also(setup).let { it.build().apply { fragments += this } }
  }

  fun group(id: String, @Nls name: String, setup: Group<Settings>.() -> Unit): NestedGroupFragment<Settings> {
    return Group(fullId, id, name, extenders).also(setup).let { it.build().apply { fragments += this } }
  }

  fun build(): MutableList<SettingsEditorFragment<Settings, *>> {
    extenders.filter { it.isApplicableTo(this) }.forEach { it.extend(this) }
    return fragments.toMutableList()
  }
}

@ApiStatus.Internal
@ApiStatus.Experimental
interface FragmentsDslBuilderExtender<Settings : FragmentedSettings> {
  val id: String

  fun extend(builder: FragmentsBuilder<Settings>)

  fun isApplicableTo(builder: FragmentsBuilder<Settings>) = builder.fullId == id

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<FragmentsDslBuilderExtender<*>>("com.intellij.fragments.dsl.builder.extender")

    inline fun <reified T : FragmentedSettings> getExtenders(startId: String): List<FragmentsDslBuilderExtender<T>> {
      return EP_NAME.extensionList.map {
        @Suppress("UNCHECKED_CAST")
        it as FragmentsDslBuilderExtender<T>
      }.filter { it.id.startsWith(startId) }
    }
  }
}

@ApiStatus.Experimental
inline fun <reified Settings : FragmentedSettings> fragments(
  title: @Nls String? = null,
  id: String,
  extenders: List<FragmentsDslBuilderExtender<Settings>> = FragmentsDslBuilderExtender.getExtenders(id),
  setup: FragmentsBuilder<Settings>.() -> Unit
): MutableList<SettingsEditorFragment<Settings, *>> = FragmentsBuilder(null, id, extenders).apply {
  if (title != null) {
    fragment("title", JLabel(title).also { it.font = JBUI.Fonts.label().deriveFont(Font.BOLD) }) {
      isRemovable = false
      commandLinePosition = -1
    }
  }
}.also(setup).build()