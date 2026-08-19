package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.nonModalWelcomeScreen.NON_MODAL_WELCOME_SCREEN_SETTING_ID
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenTabUsageCollector
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.theme.JewelTheme
import javax.swing.JComponent

@ApiStatus.Internal
abstract class WelcomeScreenRightTab(
  val project: Project,
  val contentProvider: WelcomeRightTabContentProvider,
  suppressInitialContentFocus: Boolean = false,
) {
  /**
   * While `true`, activating the tab does not pull input focus into its content, so that the left project view's
   * recent-projects search field keeps focus during the passive startup open (accessibility: up/down navigation,
   * IJPL-248588). Cleared via [enableContentFocus] once the initial startup focus has settled on the project view,
   * so later user-driven activations (Esc from the project view, clicking the tab) focus the content.
   */
  protected var contentFocusSuppressed: Boolean = suppressInitialContentFocus

  abstract val component: JComponent

  abstract fun getPreferredFocusedComponent(): JComponent

  /**
   * Switches to a custom content view if the content provider supports it.
   */
  abstract fun switchToCustomContent(provider: WelcomeRightCustomTabProvider)

  /**
   * Switches back to the default welcome screen view.
   */
  abstract fun switchToDefaultContent()

  /**
   * Re-enables moving focus into the tab content when the tab is activated. Called once the initial startup focus
   * has settled on the left project view (IJPL-248588); afterwards, activating the tab (Esc from the project view,
   * clicking the tab) focuses its content as usual.
   */
  fun enableContentFocus() {
    contentFocusSuppressed = false
  }

  companion object {
    private val projectToTabMap = mutableMapOf<ProjectId, WelcomeScreenRightTab>()

    /**
     * Gets the current WelcomeScreenRightTab instance for the given project if it exists.
     */
    fun getInstance(project: Project): WelcomeScreenRightTab? = projectToTabMap[project.projectId()]

    /**
     * Opens the welcome right tab.
     *
     * @param focusContent whether focus should be moved into the tab content once it is activated. Pass `false`
     * (default) for the passive startup open, so the left project view's recent-projects search field keeps input
     * focus (accessibility, IJPL-248588); pass `true` for an explicit user-initiated open (e.g. the "Open Welcome
     * Screen" action).
     */
    @ApiStatus.Internal
    suspend fun show(project: Project, focusContent: Boolean = false) {
      if (!isRightTabEnabled) return
      val contentProvider = WelcomeRightTabContentProvider.getSingleExtension() ?: return
      withContext(Dispatchers.EDT) {
        val tab = WelcomeScreenRightTabImpl(project, contentProvider, suppressInitialContentFocus = !focusContent)
        addToMap(project, tab)

        val settingsFile = WelcomeScreenRightTabVirtualFile(tab, project)
        val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx
        val options = FileEditorOpenOptions(reuseOpen = true, isSingletonEditorInWindow = true,
                                            forceFocus = focusContent, requestFocus = focusContent,
                                            selectAsCurrent = contentProvider.shouldBeFocused(project))
        fileEditorManager.openFile(settingsFile, options)
        WelcomeScreenTabUsageCollector.logWelcomeScreenTabOpened()
      }
    }

    private fun addToMap(project: Project, tab: WelcomeScreenRightTab) {
      val projectId = project.projectId()
      Disposer.register(project) {
        projectToTabMap.remove(projectId)
      }
      projectToTabMap[projectId] = tab
    }

    @JvmStatic
    internal var isRightTabEnabled: Boolean
      get() = AdvancedSettings.getBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID)
      set(value) = AdvancedSettings.setBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID, value)

    @Composable
    fun color(dark: Color?, light: Color?, fallback: Color): Color {
      val themeColor = if (JewelTheme.isDark) dark else light
      return themeColor ?: fallback
    }
  }
}