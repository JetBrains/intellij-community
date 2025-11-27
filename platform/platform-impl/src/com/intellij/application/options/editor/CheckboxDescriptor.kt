// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.reflect.KMutableProperty0

class CheckboxDescriptor(val name: @NlsContexts.Checkbox String,
                         @ApiStatus.Internal val getter: () -> Boolean,
                         @ApiStatus.Internal val setter: (value: Boolean) -> Unit,
                         internal val comment: @NlsContexts.DetailedDescription String? = null,
                         internal val commentAction: HyperlinkEventAction? = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE,
                         internal val groupName: @Nls String? = null) {
  constructor(name: @NlsContexts.Checkbox String,
              mutableProperty: KMutableProperty0<Boolean>,
              comment: @NlsContexts.DetailedDescription String? = null,
              commentAction: HyperlinkEventAction? = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE,
              groupName: @Nls String? = null)
    : this(name, { mutableProperty.get() }, { mutableProperty.set(it) }, comment, commentAction, groupName)

  // Preserve binary compatibility with these constructors
  constructor(
    name: @NlsContexts.Checkbox String,
    getter: () -> Boolean,
    setter: (value: Boolean) -> Unit,
    comment: @NlsContexts.DetailedDescription String? = null,
    groupName: @Nls String? = null
  ) : this(name, getter, setter, comment, /*commentAction = */null, groupName)

  constructor(
    name: @NlsContexts.Checkbox String,
    mutableProperty: KMutableProperty0<Boolean>,
    comment: @NlsContexts.DetailedDescription String? = null,
    groupName: @Nls String? = null
  ) : this(name, mutableProperty, comment, /*commentAction = */null, groupName)

  fun asUiOptionDescriptor(): BooleanOptionDescription {
    return asOptionDescriptor {
      UISettings.getInstance().fireUISettingsChanged()
    }
  }

  fun asOptionDescriptor(): BooleanOptionDescription = asOptionDescriptor(null)

  fun asOptionDescriptor(fireUpdated: (() -> Unit)?): BooleanOptionDescription {
    val optionName = if (groupName == null) {
      name
    }
    else {
      "${groupName.trim().removeSuffix(":")}: $name"
    }
    return object : BooleanOptionDescription(optionName, EDITOR_TABS_OPTIONS_ID) {
      override fun setOptionState(enabled: Boolean) {
        setter(enabled)
        fireUpdated?.invoke()
      }

      override fun isOptionEnabled() = getter.invoke()
    }
  }
}

fun Row.checkBox(ui: CheckboxDescriptor): Cell<JBCheckBox> {
  val result = checkBox(ui.name)
    .bindSelected(ui.getter, ui.setter)
  ui.comment?.let {
    if (ui.commentAction != null)
      result.comment(it, action = ui.commentAction)
    else
      result.comment(it)
  }
  return result
}
