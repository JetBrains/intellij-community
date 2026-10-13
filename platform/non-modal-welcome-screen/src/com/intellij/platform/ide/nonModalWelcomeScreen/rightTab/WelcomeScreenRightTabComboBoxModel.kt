package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafReference
import com.intellij.ide.ui.ThemeListProvider
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.openapi.keymap.impl.keymapComparator
import com.intellij.openapi.keymap.impl.ui.KeymapSchemeManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor

/**
 * Restricts the theme list to themes of a single brightness, driven by the Light/Dark switch in the theme
 * chooser popup. See [WelcomeRightTabContentProvider.isThemeChooserPopupEnabled].
 */
internal enum class ThemeBrightnessFilter {
  LIGHT,
  DARK;

  fun accepts(theme: UIThemeLookAndFeelInfo): Boolean = (this == DARK) == theme.isDark

  companion object {
    fun of(isDark: Boolean): ThemeBrightnessFilter = if (isDark) DARK else LIGHT
  }
}

/**
 * Applies an item as a live preview while the user moves through the combobox popup list, and restores the
 * original state when the popup is dismissed without a choice.
 *
 * A preview is a real state change, not a sandbox: nothing rolls it back automatically, so the caller must
 * [restore] on every path that closes the popup without a choice.
 */
internal interface ComboBoxPreviewer {
  /** State to hand back to [restore] if the popup closes without a choice. */
  fun capture(): Any?

  /** Applies the item at [index] as a preview. Returns `false` when that item must not be previewed. */
  fun preview(index: Int): Boolean

  fun restore(snapshot: Any?)
}

internal abstract class WelcomeScreenRightTabComboBoxModel<T> {
  abstract val items: List<T>

  /** Non-null when this model's items can be applied as a preview while the popup list is navigated. */
  open val previewer: ComboBoxPreviewer?
    get() = null

  abstract var currentItem: T

  abstract fun T.toName(): @NlsSafe String

  fun itemNames(): List<@NlsSafe String> = items.map { it.toName() }

  fun currentItemIndex(): Int = items.indexOf(currentItem)

  /**
   * Name of the current item regardless of whether [items] still contains it, so a filtered-out
   * selection can still be displayed.
   */
  fun currentItemName(): String = currentItem.toName()

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
     * When set, [items] only lists themes of that brightness. `null` lists every theme, which is the
     * behaviour for products that do not show the brightness switch.
     *
     * Changing this does not apply a theme: [currentItem] may end up outside [items], and
     * [currentItemName] is what keeps it visible in the combobox.
     */
    var brightnessFilter: ThemeBrightnessFilter? = null

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

    /** Brightness of the active theme, so the switch can open on the family the user is already in. */
    fun currentBrightness(): ThemeBrightnessFilter {
      return ThemeBrightnessFilter.of(laf.currentUIThemeLookAndFeel?.isDark ?: JBColor.isBright().not())
    }

    /**
     * Commits [item] as the user's actual choice.
     *
     * The [currentItem] setter short-circuits when the theme is already active, which is the normal case
     * here because a preview has just applied it — and its restart check goes with it. So the check is
     * issued explicitly in that case, and left to the setter otherwise, to avoid asking twice.
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

    override val previewer: ComboBoxPreviewer = object : ComboBoxPreviewer {
      override fun capture(): Any? = laf.currentUIThemeLookAndFeel

      override fun preview(index: Int): Boolean {
        val themeId = items.getOrNull(index)?.themeId ?: return false
        val theme = laf.findLaf(themeId) ?: return false
        // The platform's own theme quick-switcher skips these as well: a theme that needs a restart
        // cannot be shown correctly without one, so previewing it would just look broken.
        if (theme.isRestartRequired()) return false
        applyForPreview(theme)
        return true
      }

      override fun restore(snapshot: Any?) {
        applyForPreview(snapshot as? UIThemeLookAndFeelInfo ?: return)
      }
    }

    /**
     * Differs from the commit path in two ways, both deliberate:
     *
     * - `async = false`, like the platform's own theme quick-switcher. Applying asynchronously spreads the
     *   switch over several EDT events, and the window deactivation in between cancels the open popup —
     *   which then reverts the preview and drops the user out of the list.
     * - no [LafManager.checkRestart]: a preview must not pop a restart prompt for a theme the user has
     *   not actually chosen.
     */
    private fun applyForPreview(theme: UIThemeLookAndFeelInfo) {
      QuickChangeLookAndFeel.switchLafAndUpdateUI(laf, theme, false)
    }

    /**
     * Every theme name, ignoring [brightnessFilter], so a control can be sized once and not resize as the
     * filter narrows the list.
     */
    fun allItemNames(): List<String> = allItems().map { it.name }

    /** Every theme, ignoring [brightnessFilter]. */
    fun allItems(): List<LafReference> = getThemes(filter = null)

    private fun getThemes(filter: ThemeBrightnessFilter? = brightnessFilter): List<LafReference> {
      // Filtering happens before mapping, because only UIThemeLookAndFeelInfo carries isDark.
      val groupedThemes = ThemeListProvider.getInstance().getShownThemes()
      return groupedThemes.infos.asSequence()
        .flatMap { it.items }
        .filter { filter == null || filter.accepts(it) }
        .map { LafReference(it.name, it.id) }
        .toList()
    }

    private val laf: LafManager
      get() = LafManager.getInstance()
  }
}