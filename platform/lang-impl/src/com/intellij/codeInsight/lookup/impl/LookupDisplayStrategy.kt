// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.ui.accessibility.ScreenReader
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color
import java.awt.Point

/**
 * Can be used to customize a method of displaying of the lookup, for example
 * - to display the lookup not as a hint, but using some other UI,
 *   like embed it inside some window,
 * - to change its position or background color.
 */
@ApiStatus.Experimental
@Internal
interface LookupDisplayStrategy {
  companion object {
    @JvmField
    @ApiStatus.Experimental
    @Internal
    val EP_NAME: ExtensionPointName<LookupDisplayStrategy> = ExtensionPointName("com.intellij.lookup.displayStrategy")

    @JvmStatic
    fun getStrategy(e: Editor): LookupDisplayStrategy = EP_NAME.extensionList.firstOrNull { it.isAvailable(e) } ?: LookupDisplayAsHintStrategy
  }

  fun isAvailable(editor: Editor): Boolean
  fun updateLocation(lookup: LookupImpl, editor: Editor, point: Point)
  fun showLookup(lookup: LookupImpl, editor: Editor, point: Point)
  fun hideLookup(lookup: LookupImpl, editor: Editor)
  val backgroundColor: Color
}

@ApiStatus.Experimental
@Internal
object LookupDisplayAsHintStrategy : LookupDisplayStrategy {
  override fun isAvailable(editor: Editor): Boolean = true

  override fun updateLocation(lookup: LookupImpl, editor: Editor, point: Point) {
    HintManagerImpl.updateLocation(lookup, editor, point)
  }

  override fun showLookup(lookup: LookupImpl, editor: Editor, point: Point) {
    HintManagerImpl.getInstanceImpl().showEditorHint(
      lookup, editor, point, HintManager.HIDE_BY_ESCAPE or HintManager.UPDATE_BY_SCROLLING, 0, false,
      HintManagerImpl.createHintHint(editor, point, lookup, HintManager.UNDER).setRequestFocus
      (ScreenReader.isActive()).setAwtTooltip
      (false))
  }

  override fun hideLookup(lookup: LookupImpl, e: Editor) {
    lookup.hide(false)
  }

  override val backgroundColor: Color
    get() = LookupCellRenderer.BACKGROUND_COLOR
}

