// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.ide.ActivityTracker
import com.intellij.ide.ui.LafReference
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenComboBoxKind
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenTabUsageCollector
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.ThemeModel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.Timer

private const val PREVIEW_DELAY_MS = 500

/** The unscaled room under the list and the sample. */
private const val BOTTOM_PADDING = 10

private const val APPEARANCE_CONFIGURABLE_ID = "preferences.lookFeel"

/**
 * The chooser of the Theme control of the welcome screen.
 *
 * The list applies the highlighted theme as a preview and restores the theme of the user when the popup closes
 * without a choice. A switch in the header narrows the list to light or dark themes.
 *
 * The chooser is a standalone popup. The popup of a combobox belongs to the combobox, and it closes when
 * a theme change moves the frame. A list popup has no place for the header.
 */
internal class WelcomeScreenThemeChooserPopup(
  private val model: ThemeModel,
  private val sample: WelcomeRightTabContentProvider.ThemePreviewSample?,
) {
  fun createPopup(owner: JComponent): JBPopup {
    val snapshot = model.capture()
    val activeTheme = model.currentItem
    var filter = model.currentBrightness()

    val moreItem = LafReference(NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.theme.more.in.appearance"), "")
    var firstCustomThemeId: String? = null
    fun listItems(): List<LafReference> {
      val themes = model.themes(filter)
      firstCustomThemeId = themes.firstOrNull(model::isCustom)?.themeId
      return themes + moreItem
    }

    val listModel = CollectionListModel(listItems())
    val list = JBList(listModel)
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    // The renderer shortens the names, so there is nothing to expand.
    list.expandableItemsHandler.isEnabled = false
    list.setSelectedValue(activeTheme, true)

    val previewTimer = Timer(PREVIEW_DELAY_MS) { list.selectedValue?.let(model::preview) }
    previewTimer.isRepeats = false
    list.addListSelectionListener { event ->
      if (!event.valueIsAdjusting && list.selectedIndex >= 0) {
        previewTimer.restart()
      }
    }

    val disposable = Disposer.newDisposable("WelcomeScreenThemeChooserPopup")
    // The control shows the theme of the user while a preview is applied.
    model.pinnedItem = activeTheme
    Disposer.register(disposable) {
      model.pinnedItem = null
      ActivityTracker.getInstance().inc()
    }

    val header = createThemeChooserHeader(filter, hasSample = sample != null) { selected ->
      filter = selected
      listModel.replaceAll(listItems())
      list.selectedIndex = maxOf(listModel.getElementIndex(activeTheme), 0)
      list.ensureIndexIsVisible(list.selectedIndex)
    }

    lateinit var popup: JBPopup
    var isChosen = false
    var isClosed = false

    val builder = PopupChooserBuilder(list)
    builder.setRenderer(createThemeListRenderer(list, model, moreItem, activeTheme.themeId) { firstCustomThemeId })
    builder.setNorthComponent(header)
    sample?.let { builder.setEastComponent(createThemeSample(it, disposable)) }
    builder.setSouthComponent(createBottomPadding())
    builder.setAutoselectOnMouseMove(true)
    builder.setRequestFocus(true)
    builder.setCancelOnClickOutside(true)
    builder.setCancelKeyEnabled(true)
    builder.setResizable(false)
    builder.setMovable(false)
    builder.setMinSize(Dimension(owner.width, 0))
    builder.setCloseOnEnter(false)
    builder.setItemChosenCallback(Runnable {
      val selected = list.selectedValue ?: return@Runnable
      if (selected === moreItem) {
        popup.cancel()
        ApplicationManager.getApplication().invokeLater(::openAppearanceSettings)
        return@Runnable
      }
      isChosen = true
      previewTimer.stop()
      model.commit(selected)
      WelcomeScreenTabUsageCollector.logComboBoxValueChanged(WelcomeScreenComboBoxKind.THEME)
      popup.cancel()
    })
    builder.setCancelCallback(Computable {
      if (!isClosed) {
        isClosed = true
        previewTimer.stop()
        if (!isChosen) {
          model.restore(snapshot)
        }
      }
      true
    })

    popup = builder.createPopup()
    Disposer.register(popup, disposable)
    closeOnIdeScaleChange(popup, disposable)

    builder.scrollPane?.let { scrollPane ->
      scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      // The builder takes the height from the content, so the height of the sample is set here.
      scrollPane.preferredSize = Dimension(scrollPane.preferredSize.width, JBUI.scale(SAMPLE_HEIGHT))
    }
    return popup
  }

  // The sizes of the popup are set once in pixels. The zoom resizes the sample and the header, and then the list is too narrow.
  private fun closeOnIdeScaleChange(popup: JBPopup, disposable: Disposable) {
    val scaleOnOpen = UISettingsUtils.getInstance().currentIdeScale
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(UISettingsListener.TOPIC, UISettingsListener {
      if (UISettingsUtils.getInstance().currentIdeScale != scaleOnOpen) {
        ApplicationManager.getApplication().invokeLater {
          if (!popup.isDisposed) popup.cancel()
        }
      }
    })
  }

  private fun openAppearanceSettings() {
    ShowSettingsUtil.getInstance().showSettingsDialog(null, { it is SearchableConfigurable && it.id == APPEARANCE_CONFIGURABLE_ID }, null)
  }

  private fun createBottomPadding(): JComponent {
    val padding = JPanel()
    padding.isOpaque = false
    padding.preferredSize = Dimension(0, JBUI.scale(BOTTOM_PADDING))
    return padding
  }
}
