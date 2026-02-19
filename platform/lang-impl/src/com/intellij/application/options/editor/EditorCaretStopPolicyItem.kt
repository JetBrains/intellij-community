// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.editor.actions.CaretStopBoundary
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import org.jetbrains.annotations.Nls
import javax.swing.ListCellRenderer

internal interface EditorCaretStopPolicyItem {
  val title: String
  val caretStopBoundary: CaretStopBoundary
  val osDefault: OsDefault

  companion object {
    internal inline fun <reified E> findMatchingItem(caretStopBoundary: CaretStopBoundary): E
      where E : Enum<E>,
            E : EditorCaretStopPolicyItem = enumValues<E>().find { it.caretStopBoundary == caretStopBoundary }
                                            ?: checkNotNull(enumValues<E>().find { it.osDefault.isIdeDefault })
    internal fun String.appendHint(hint: String): String =
      if (hint.isBlank()) this else "$this ($hint)"

    internal fun createRenderer(itemWithSeparator: EditorCaretStopPolicyItem): ListCellRenderer<EditorCaretStopPolicyItem?> {
      return listCellRenderer("") {
        text(value.title)
        text(value.osDefault.hint) {
          foreground = greyForeground
        }
        if (value == itemWithSeparator) {
          separator { }
        }
      }
    }
  }

  enum class OsDefault(@Nls open val hint: String = "") {
    UNIX(hint = when {
           SystemInfo.isLinux -> ApplicationBundle.message("combobox.item.hint.os.default.linux")
           SystemInfo.isMac -> ApplicationBundle.message("combobox.item.hint.os.default.mac")
           else -> ApplicationBundle.message("combobox.item.hint.os.default.unix")
         }),
    WINDOWS(hint = ApplicationBundle.message("combobox.item.hint.os.default.windows")),
    IDE(hint = run {
      // Don't let long product names like "Android Studio" stretch the combobox presentation.
      val shortProductName = ApplicationNamesInfo.getInstance().fullProductName.takeIf { it.length <= 8 } ?: "IDE"
      ApplicationBundle.message("combobox.item.hint.ide.default", shortProductName)
    }),
    NONE;

    val isIdeDefault: Boolean get() = this == IDE
  }

  enum class WordBoundary(override val title: String,
                          override val caretStopBoundary: CaretStopBoundary,
                          override val osDefault: OsDefault = OsDefault.NONE) : EditorCaretStopPolicyItem {
    STICK_TO_WORD_BOUNDARIES(ApplicationBundle.message("combobox.item.stick.to.word.boundaries"), CaretStopBoundary.CURRENT,
                             OsDefault.IDE),
    JUMP_TO_WORD_START(ApplicationBundle.message("combobox.item.jump.to.word.start"), CaretStopBoundary.START,
                       OsDefault.WINDOWS),
    JUMP_TO_WORD_END(ApplicationBundle.message("combobox.item.jump.to.word.end"), CaretStopBoundary.END),
    JUMP_TO_NEIGHBORING_WORD(ApplicationBundle.message("combobox.item.jump.to.neighboring.word"), CaretStopBoundary.NEIGHBOR),
    STOP_AT_ALL_WORD_BOUNDARIES(ApplicationBundle.message("combobox.item.stop.at.all.word.boundaries"), CaretStopBoundary.BOTH);

    override fun toString(): String = title.appendHint(osDefault.hint)
    companion object {
      @JvmStatic
      fun itemForBoundary(caretStopBoundary: CaretStopBoundary): WordBoundary = findMatchingItem(caretStopBoundary)
    }
  }

  enum class LineBoundary(override val title: String,
                          override val caretStopBoundary: CaretStopBoundary,
                          override val osDefault: OsDefault = OsDefault.NONE) : EditorCaretStopPolicyItem {
    JUMP_TO_NEIGHBORING_LINE(ApplicationBundle.message("combobox.item.jump.to.neighboring.line"), CaretStopBoundary.NEIGHBOR,
                             OsDefault.IDE),
    SKIP_LINE_BREAK(ApplicationBundle.message("combobox.item.proceed.to.word.boundary"), CaretStopBoundary.NONE,
                    OsDefault.UNIX),
    STOP_AT_BOTH_LINE_BOUNDARIES(ApplicationBundle.message("combobox.item.stop.at.both.line.ends"), CaretStopBoundary.BOTH,
                                 OsDefault.WINDOWS),
    STAY_ON_CURRENT_LINE(ApplicationBundle.message("combobox.item.stay.on.current.line"), CaretStopBoundary.CURRENT),
    JUMP_TO_LINE_START(ApplicationBundle.message("combobox.item.stop.at.line.start"), CaretStopBoundary.START),
    JUMP_TO_LINE_END(ApplicationBundle.message("combobox.item.stop.at.line.end"), CaretStopBoundary.END);

    override fun toString(): String = title.appendHint(osDefault.hint)
    companion object {
      @JvmStatic
      fun itemForBoundary(caretStopBoundary: CaretStopBoundary): LineBoundary = findMatchingItem(caretStopBoundary)
    }
  }
}
