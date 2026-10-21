package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafReference
import com.intellij.ide.ui.ThemeListProvider
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.ide.ui.laf.isThemeFromPlugin
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.openapi.keymap.impl.keymapComparator
import com.intellij.openapi.keymap.impl.ui.KeymapSchemeManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import javax.swing.Icon

internal abstract class WelcomeScreenRightTabComboBoxModel<T> {
  abstract val items: List<T>

  abstract var currentItem: T

  abstract fun T.toName(): @NlsSafe String

  fun itemNames(): List<@NlsSafe String> = items.map { it.toName() }

  fun currentItemIndex(): Int = items.indexOf(currentItem)

  /** The name that the control shows. */
  open fun currentItemName(): @NlsSafe String = currentItem.toName()

  fun setByIndex(index: Int, itemName: String) {
    val item = items[index]
    if (item.toName() == itemName) {
      currentItem = item
    }
  }

  class KeymapModel : WelcomeScreenRightTabComboBoxModel<Keymap>() {
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

  class ThemeModel : WelcomeScreenRightTabComboBoxModel<LafReference>() {
    internal companion object {
      fun getIcon() = if (JBColor.isBright()) AllIcons.MeetNewUi.LightTheme else AllIcons.MeetNewUi.DarkTheme
    }

    /**
     * The theme that the control shows instead of the active one, or `null` to show the active one.
     * The theme chooser pins the theme that the user has, because a preview changes the active theme.
     */
    var pinnedItem: LafReference? = null

    override val items: List<LafReference>
      get() = themes(filter = null)

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

    override fun currentItemName(): @NlsSafe String = (pinnedItem ?: currentItem).name

    /** The icon of the control. It follows the brightness of [pinnedItem], or of the active theme. */
    fun currentIcon(): Icon {
      val isDark = pinnedItem?.let { findTheme(it)?.isDark } ?: return getIcon()
      return if (isDark) AllIcons.MeetNewUi.DarkTheme else AllIcons.MeetNewUi.LightTheme
    }

    fun currentBrightness(): ThemeBrightnessFilter {
      return ThemeBrightnessFilter.of(laf.currentUIThemeLookAndFeel?.isDark ?: JBColor.isBright().not())
    }

    /** The themes that [filter] accepts, or all themes for `null`. The themes of plugins come last. */
    fun themes(filter: ThemeBrightnessFilter?): List<LafReference> {
      val themes = ThemeListProvider.getInstance().getShownThemes().infos
        .flatMap { it.items }
        .filter { filter == null || filter.accepts(it) }
      return themes.customLast { it.isThemeFromPlugin }.map { LafReference(it.name, it.id) }
    }

    fun isCustom(item: LafReference): Boolean = findTheme(item)?.isThemeFromPlugin == true

    fun isRestartRequired(item: LafReference): Boolean = findTheme(item)?.isRestartRequired() == true

    private fun findTheme(item: LafReference): UIThemeLookAndFeelInfo? {
      return ThemeListProvider.getInstance().getShownThemes().infos.firstNotNullOfOrNull { group ->
        group.items.find { it.id == item.themeId }
      }
    }

    /** The theme to give to [restore] when a preview ends without a choice. */
    fun capture(): UIThemeLookAndFeelInfo? = laf.currentUIThemeLookAndFeel

    /** Applies [item] without a restart prompt. Returns `false` when [item] is not a theme or needs a restart to show. */
    fun preview(item: LafReference): Boolean {
      val theme = laf.findLaf(item.themeId) ?: return false
      if (theme.isRestartRequired()) return false
      applyForPreview(theme)
      return true
    }

    fun restore(snapshot: UIThemeLookAndFeelInfo?) {
      snapshot?.let(::applyForPreview)
    }

    /**
     * Applies [item] as the choice of the user. A preview has usually applied the theme already,
     * and then the [currentItem] setter does nothing, so the restart prompt comes from here.
     */
    fun commit(item: LafReference) {
      val appliedByPreview = laf.currentUIThemeLookAndFeel?.id == item.themeId
      currentItem = item
      if (appliedByPreview) {
        ApplicationManager.getApplication().invokeLater {
          laf.checkRestart()
        }
      }
    }

    // A synchronous switch keeps the window active, so the open popup survives it.
    private fun applyForPreview(theme: UIThemeLookAndFeelInfo) {
      QuickChangeLookAndFeel.switchLafAndUpdateUI(laf, theme, false)
    }

    private val laf: LafManager
      get() = LafManager.getInstance()
  }
}

/** Moves the items that [isCustom] accepts to the end. The order inside both parts stays the same. */
internal fun <T> List<T>.customLast(isCustom: (T) -> Boolean): List<T> {
  val (custom, other) = partition(isCustom)
  return other + custom
}
