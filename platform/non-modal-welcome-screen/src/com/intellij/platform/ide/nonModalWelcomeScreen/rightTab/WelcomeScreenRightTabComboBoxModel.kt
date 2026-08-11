package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.ide.GeneralSettings
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.LafReference
import com.intellij.ide.ui.ThemeListProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.impl.KeymapManagerImpl
import com.intellij.openapi.keymap.impl.keymapComparator
import com.intellij.openapi.keymap.impl.ui.KeymapSchemeManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenProjectScopeHolder
import kotlinx.coroutines.launch

internal abstract class WelcomeScreenRightTabComboBoxModel<T> {
  abstract val items: List<T>

  abstract var currentItem: T

  abstract fun T.toName(): String

  fun itemNames(): List<String> = items.map { it.toName() }

  fun currentItemIndex(): Int = items.indexOf(currentItem)

  abstract fun externalUpdateListener(project: Project): ((Int) -> Unit) -> Unit

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

    override fun externalUpdateListener(project: Project): ((Int) -> Unit) -> Unit {
      return { stateListener ->
        ApplicationManager.getApplication().getMessageBus().connect(project)
          .subscribe<KeymapManagerListener>(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
            override fun activeKeymapChanged(keymap: Keymap?) {
              stateListener(currentItemIndex())
            }
          })
      }
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

    override fun externalUpdateListener(project: Project): ((Int) -> Unit) -> Unit {
      return { stateListener ->
        ApplicationManager.getApplication().getMessageBus().connect(project)
          .subscribe<LafManagerListener>(LafManagerListener.TOPIC, LafManagerListener {
            stateListener(currentItemIndex())
          })
      }
    }

    private fun getThemes(): List<LafReference> {
      val groupedThemes = ThemeListProvider.getInstance().getShownThemes()
      return groupedThemes.infos.flatMap { it.items }.map { LafReference(it.name, it.id) }
    }

    private val laf: LafManager
      get() = LafManager.getInstance()
  }

  class StartupSwitchModel : WelcomeScreenRightTabComboBoxModel<Boolean>() {
    val settings = GeneralSettings.getInstance()

    override val items: List<Boolean> = listOf(false, true)

    override var currentItem: Boolean
      get() = settings.isReopenLastProject
      set(value) {
        settings.isReopenLastProject = value
      }

    override fun Boolean.toName(): String {
      return if (this)
        NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.startup.switch.reopen")
      else
        NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.startup.switch.welcome")
    }

    override fun externalUpdateListener(project: Project): ((Int) -> Unit) -> Unit = { stateListener ->
      WelcomeScreenProjectScopeHolder.getInstance(project).coroutineScope.launch {
        settings.propertyChangedFlow.collect {
          if (it == GeneralSettings.PropertyNames.reopenLastProject) {
            stateListener(currentItemIndex())
          }
        }
      }
    }
  }
}