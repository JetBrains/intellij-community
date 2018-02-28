// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.ui.noria.*
import java.util.function.Function

object NameSuggesterSelection {
  val All: Function<String, TextRange> = Function { TextRange.allOf(it)}
  val None: Function<String, TextRange> = Function { TextRange.EMPTY_RANGE}
  val NameWithoutExtension: Function<String, TextRange> = Function {
    val pos = it.lastIndexOf('.')
    if (pos > 0) {
      TextRange.create(0, pos)
    } else {
      TextRange.allOf(it)
    }}
}

data class NameSuggester(val suggestedNames: List<String>,
                         val selection: (String) -> TextRange,
                         val onChange: (String) -> Unit,
                         val project: Project,
                         val fileType: FileType,
                         val editor: Editor?,
                         override var autoFocus: Boolean = false) : BaseProps by BasePropsData(), Focusable

val NAME_SUGGESTER_COMPONENT_TYPE = "nameSuggester"
val nameSuggester = primitiveComponent<NameSuggester>(NAME_SUGGESTER_COMPONENT_TYPE)

class NameSuggesterComponentType : PrimitiveComponentType<NameSuggestionsField, NameSuggester> {
  override val type: String = NAME_SUGGESTER_COMPONENT_TYPE

  override fun createNode(e: NameSuggester): NameSuggestionsField {
    val field = NameSuggestionsField(e.suggestedNames.toTypedArray(), e.project, e.fileType, e.editor)
    val sel = e.selection(field.enteredName)
    if (sel != TextRange.EMPTY_RANGE) {
      field.select(sel.startOffset, sel.endOffset)
    }
    val listener = NameSuggestionsField.DataChanged {
      e.onChange(field.enteredName)
    }
    field.addDataChangedListener(listener)
    field.putClientProperty(LISTENER, listener)
    return field
  }

  override fun update(info: UpdateInfo<NameSuggestionsField, NameSuggester>) {
    if (info.new.onChange != info.old.onChange) {
      val oldListener = info.c.getClientProperty(LISTENER) as? NameSuggestionsField.DataChanged
      if (oldListener != null) {
        info.c.removeDataChangedListener(oldListener)
      }
      val listener = NameSuggestionsField.DataChanged {
        info.new.onChange(info.c.enteredName)
      }
      info.c.addDataChangedListener(listener)
      info.c.putClientProperty(LISTENER, listener)
    }
    info.updateProp(NameSuggester::suggestedNames, {setSuggestions(it.toTypedArray())})
  }
}
