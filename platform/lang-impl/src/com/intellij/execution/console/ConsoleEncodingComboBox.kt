// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.console

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.encoding.EncodingManager
import com.intellij.openapi.vfs.encoding.EncodingReference
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.nio.charset.Charset

@ApiStatus.Internal
class ConsoleEncodingComboBox : ComboBox<ConsoleEncodingComboBox.EncodingItem>() {
  interface EncodingItem {
    val displayName: @Nls @NlsContexts.Label String
  }

  private data class CharsetItem(val reference: EncodingReference) : EncodingItem {
    constructor(charset: Charset) : this(EncodingReference(charset))

    override val displayName: @NlsSafe String
      get() {
        return reference.charset?.displayName()
               ?: IdeBundle.message("encoding.name.system.default", CharsetToolkit.getDefaultSystemCharset().displayName())
      }

    override fun toString(): String {
      return displayName
    }

    companion object {
      val DEFAULT = CharsetItem(EncodingReference.DEFAULT)
    }
  }


  init {
    model = CollectionComboBoxModel<EncodingItem>()

    renderer = listCellRenderer<EncodingItem?> {
      val value = value

      if (value == null) {
        text("")
      }
      else {
        text(value.displayName)

        when (value) {
          firstFavorite -> separator { text = ApplicationBundle.message("combobox.console.favorites.separator.label") }
          firstMore -> separator { text = ApplicationBundle.message("combobox.console.more.separator.label") }
        }
      }
    }

    isSwingPopup = false
  }

  private val listModel: CollectionComboBoxModel<EncodingItem>
    get() = model as CollectionComboBoxModel<EncodingItem>

  private var firstFavorite: EncodingItem? = null
  private var firstMore: EncodingItem? = null

  /**
   * Construct combobox model and reset item
   * Model:
   * ```
   *    Use system encoding
   *    --- Favorites
   *    Favorite 1
   *    ...
   *    Favorite n
   *    --- More
   *    Charset 1
   *    ...
   *    Charset n
   * ```
   */
  fun reset(reference: EncodingReference) {
    val encodingManager = EncodingManager.getInstance()
    val favorites = encodingManager.favorites.map { CharsetItem(it) }
    val available = Charset.availableCharsets().values.map { CharsetItem(it) }

    firstFavorite = favorites.getOrNull(0)
    firstMore = available.getOrNull(0)

    listModel.add(CharsetItem.DEFAULT)
    listModel.add(favorites)
    listModel.add(available)

    listModel.selectedItem = CharsetItem(reference)
  }

  fun getSelectedEncodingReference(): EncodingReference {
    return if (selectedItem is CharsetItem) {
      (selectedItem as CharsetItem).reference
    }
    else {
      if (selectedItem != null) {
        LOG.error("Encoding should be either DEFAULT or an actual Charset. Got $selectedItem")
      }
      EncodingReference.DEFAULT
    }
  }
  companion object {
    private val LOG = Logger.getInstance(ConsoleEncodingComboBox::class.java)
  }
}


