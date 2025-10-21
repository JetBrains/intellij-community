package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafReference
import com.intellij.ide.ui.ThemeListProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.openapi.keymap.impl.keymapComparator
import com.intellij.openapi.keymap.impl.ui.KeymapSchemeManager

internal abstract class WelcomeScreenRightTabComboBoxModel<T> {
  abstract val items: List<T>

  abstract var currentItem: T

  abstract fun T.toName(): String

  fun itemNames(): List<String> = items.map { it.toName() }

  fun currentItemIndex(): Int = items.indexOf(currentItem)

  fun setByIndex(index: Int, itemName: String) {
    val item = items[index]
    if (item.toName() == itemName) {
      currentItem = item
    }
  }

  class KeymapModel() : WelcomeScreenRightTabComboBoxModel<Keymap>() {
    override val items: List<Keymap> = getKeymaps()

    override var currentItem: Keymap
      get() = keymapManager.activeKeymap
      set(value) {
        keymapManager.activeKeymap = value
      }

    override fun Keymap.toName(): String = when (name) {
      "Mac OS X 10.5+" -> "IntelliJ (macOS)"
      "\$default" -> "IntelliJ (Windows)"
      else -> toString()
    }

    private fun getKeymaps(): List<Keymap> {
      return keymapManager.getKeymaps(KeymapSchemeManager.FILTER).sortedWith(keymapComparator)
    }

    private val keymapManager: KeymapManagerImpl
      get() = KeymapManager.getInstance() as KeymapManagerImpl
  }

  class ThemeModel() : WelcomeScreenRightTabComboBoxModel<LafReference>() {
    override val items: List<LafReference>
      get() = getThemes()

    override var currentItem: LafReference
      get() = laf.lookAndFeelReference
      set(value) {
        val newLaf = laf.findLaf(value.themeId)
        if (laf.getCurrentUIThemeLookAndFeel() == newLaf) {
          return
        }

        ApplicationManager.getApplication().invokeLater {
          QuickChangeLookAndFeel.switchLafAndUpdateUI(laf, newLaf, true)
          LafManager.getInstance().checkRestart()
        }
      }

    override fun LafReference.toName(): String = name

    private fun getThemes(): List<LafReference> {
      val groupedThemes = ThemeListProvider.Companion.getInstance().getShownThemes()
      return groupedThemes.infos.flatMap { it.items }.map { LafReference(it.name, it.id) }
    }

    private val laf: LafManager
      get() = LafManager.getInstance()
  }
}