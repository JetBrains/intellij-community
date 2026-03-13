// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.concurrency.currentThreadContext
import com.intellij.ide.DataManager
import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.actions.SettingsEntryPointAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.UiComponentsSearchUtil
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.platform.ide.newUiOnboarding.newUi.NewUiOnboardingBean
import com.intellij.ui.ColorUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.WebAnimationUtils
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem
import com.intellij.ui.popup.WizardPopup
import com.intellij.util.SlowOperations
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.SwingUtilities

@ApiStatus.Internal
object NewUiOnboardingUtil {
  const val ONBOARDING_PROPOSED_VERSION: String = "experimental.ui.onboarding.proposed.version"
  const val NEW_UI_ON_FIRST_STARTUP: String = "experimental.ui.on.first.startup"

  internal const val MEET_ISLANDS_TOUR_COVER_IMAGE_PATH: String = "newUiOnboarding/meetIslandsTourCover.png"
  internal const val SHOW_TOOL_WINDOW_NAMES_IMAGE_PATH: String = "newUiOnboarding/showToolWindowNamesTour.png"

  private const val LOTTIE_SCRIPT_PATH = "newUiOnboarding/lottie.js"
  private const val LIGHT_SUFFIX = ""
  private const val LIGHT_HEADER_SUFFIX = "_light_header"
  private const val DARK_SUFFIX = "_dark"

  val isOnboardingEnabled: Boolean
    get() = Registry.`is`("ide.experimental.ui.onboarding", true)
            && !DistractionFreeModeController.shouldMinimizeCustomHeader()
            && NewUiOnboardingBean.isPresent

  enum class OnboardingType {
    NEW_UI_ONBOARDING
  }

  internal fun getImage(path: String): Icon {
    return IconLoader.getIcon(path, NewUiOnboardingUtil::class.java.classLoader)
  }

  fun shouldProposeOnboarding(type: OnboardingType): Boolean {
    val propertiesComponent = PropertiesComponent.getInstance()
    val proposeOnboarding = propertiesComponent.getBoolean(ExperimentalUI.NEW_UI_SWITCH)
                            && ((!propertiesComponent.getBoolean(NEW_UI_ON_FIRST_STARTUP)
                                 && !propertiesComponent.isValueSet(ONBOARDING_PROPOSED_VERSION))
                                || ExperimentalUI.forcedSwitchedUi)

    return ExperimentalUI.isNewUI()
           && isOnboardingEnabled
           && (proposeOnboarding ||
               (type == OnboardingType.NEW_UI_ONBOARDING && ExperimentalUI.SHOW_NEW_UI_ONBOARDING_ON_START))
  }

  fun getHelpLink(topic: String): String {
    val ideHelpName = NewUiOnboardingBean.getInstance().ideHelpName
    return "https://www.jetbrains.com/help/$ideHelpName/$topic"
  }

  fun @Nls String.dropMnemonic(): @Nls String {
    return TextWithMnemonic.parse(this).dropMnemonic(true).text
  }

  suspend fun showToolbarComboButtonPopup(button: ToolbarComboButton, action: ExpandableComboAction, disposable: Disposable): JBPopup? {
    return showNonClosablePopup(
      disposable,
      createPopup = {
        val dataContext = DataManager.getInstance().getDataContext(button)
        val event = AnActionEvent.createFromDataContext(ActionPlaces.NEW_UI_ONBOARDING, action.templatePresentation.clone(), dataContext)
        performActionUpdate(action, event)

        val popup = action.createPopup(event)
        popup?.addListener(object : JBPopupListener {
          override fun beforeShown(event: LightweightWindowEvent) {
            button.model.setSelected(true)
          }

          override fun onClosed(event: LightweightWindowEvent) {
            button.model.setSelected(false)
          }
        })
        popup
      },
      showPopup = { popup ->
        popup.setRequestFocus(false)
        popup.showUnderneathOf(button)
      }
    )
  }

  suspend fun showNonClosablePopup(
    disposable: Disposable,
    createPopup: suspend () -> JBPopup?,
    showPopup: (JBPopup) -> Unit
  ): JBPopup? {
    val popup = createPopup() ?: return null
    Disposer.register(disposable) { popup.closeOk(null) }
    // Can't provide parent coroutine scope here, but need it to reopen the popup.
    // All created scopes will be closed when onboarding step dispose.
    val coroutineScope = CoroutineScope(currentThreadContext())
    Disposer.register(disposable) { coroutineScope.cancel() }

    if (popup is WizardPopup) {
      // do not install PopupDispatcher, otherwise some cancel settings that are installed below won't work.
      popup.setActiveRoot(false)
    }
    (popup as AbstractPopup).apply {
      setCancelOnMouseOutCallback(null)
      setCancelOnOtherWindowOpen(false)
      setCancelOnWindowDeactivation(false)
      isCancelOnClickOutside = false
      isCancelKeyEnabled = false
    }

    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        coroutineScope.launch(Dispatchers.EDT) {
          delay(500)
          ensureActive()
          showNonClosablePopup(disposable, createPopup, showPopup)
        }
      }
    })
    showPopup(popup)
    return popup
  }

  suspend fun createPopupFromActionButton(
    button: ActionButton,
    place: String = ActionPlaces.NEW_UI_ONBOARDING,
    doCreatePopup: suspend (AnActionEvent) -> JBPopup?,
  ): JBPopup? {
    val action = button.action
    val context = DataManager.getInstance().getDataContext(button)
    val event = AnActionEvent.createFromInputEvent(null, place, button.presentation, context)
    performActionUpdate(action, event)

    var popup: JBPopup?
    // wrap popup creation into SlowOperations.ACTION_PERFORM, otherwise there can be a lot of exceptions
    SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use {
      popup = doCreatePopup(event)
      if (popup != null) {
        Toggleable.setSelected(button.presentation, true)
      }
    }
    return popup
  }

  fun isPopupLeftSide(popup: JBPopup): Boolean {
    val c = popup.content
    val frame = UIUtil.findUltimateParent(c) ?: return false
    val leftArea = SwingUtilities.convertPoint(c, 0, 0, frame).x
    val rightArea = frame.width - c.width - leftArea

    return leftArea > rightArea
  }

  private suspend fun performActionUpdate(action: AnAction, event: AnActionEvent) {
    val dispatcher = if (action.actionUpdateThread == ActionUpdateThread.BGT) Dispatchers.Default else Dispatchers.EDT
    withContext(dispatcher) {
      readAction {
        ActionUtil.updateAction(action, event)
      }
    }
  }

  fun convertPointToFrame(project: Project, source: Component, point: Point): RelativePoint? {
    val frame = WindowManager.getInstance().getFrame(project) ?: return null
    val framePoint = SwingUtilities.convertPoint(source, point, frame)
    return RelativePoint(frame, framePoint)
  }

  /**
   * Creates an HTML page with provided lottie animation
   * @return a pair of html page text and a size of the animation
   */
  fun createLottieAnimationPage(lottieJsonPath: String, classLoader: ClassLoader): Pair<String, Dimension>? {
    val pathVariants = getAnimationPathVariants(lottieJsonPath)
    // read json using provided class loader, because it can be in the plugin jar
    val lottieJson = pathVariants.firstNotNullOfOrNull { readResource(it, classLoader) } ?: run {
      LOG.error("Failed to read all of the following: $pathVariants")
      return null
    }
    val size = getLottieImageSize(lottieJson) ?: return null

    // read js script by platform class loader, because it is in the platform jar
    val lottieScript = readResource(LOTTIE_SCRIPT_PATH, NewUiOnboardingUtil::class.java.classLoader) ?: run {
      LOG.error("Failed to read resource by path: $LOTTIE_SCRIPT_PATH")
      return null
    }
    val background = JBUI.CurrentTheme.GotItTooltip.animationBackground(false)
    val htmlPage = WebAnimationUtils.createLottieAnimationPage(lottieJson, lottieScript, background)
    return htmlPage to size
  }

  suspend fun createSettingsEntryPointPopup(project: Project, disposable: CheckedDisposable): JBPopup? {
    val settingsButton = UiComponentsSearchUtil.findUiComponent(project) { button: ActionButton ->
      button.action is SettingsEntryPointAction
    } ?: return null

    val action = settingsButton.action as SettingsEntryPointAction

    val result = showNonClosablePopup(
      disposable,
      createPopup = {
        createPopupFromActionButton(settingsButton, place = ActionPlaces.getPopupPlace(ActionPlaces.MAIN_TOOLBAR)) { event ->
          action.createPopup(event)
        }
      },
      showPopup = { popup -> popup.showUnderneathOf(settingsButton) }
    ) ?: return null

    yield()  // Wait for the popup to be shown

    return result
  }

  /**
   * Returns `true` if selected
   */
  suspend fun selectAction(list: JBList<*>, actionId: String): Boolean {
    val pluginsActionIndex = findActionItem(list, actionId)?.first ?: return false
    list.setSelectedIndex(pluginsActionIndex)
    return true
  }

  suspend fun findActionItem(list: JBList<*>, actionId: String): Pair<Int, ActionItem>? {
    val actionManager = serviceAsync<ActionManager>()

    for (i in 0 until list.model.size) {
      val element = list.model.getElementAt(i) as? ActionItem ?: continue
      if (actionManager.getId(element.action) == actionId) {
        return i to element
      }
    }

    return null
  }

  suspend fun findActionItemBounds(list: JBList<*>, actionId: String): Rectangle? {
    val pluginsActionIndex = findActionItem(list, actionId)?.first ?: return null

    return list.getCellBounds(pluginsActionIndex, pluginsActionIndex)
  }

  private fun getAnimationPathVariants(path: String): List<String> {
    val extension = path.substringAfterLast(".", missingDelimiterValue = "")
    val pathWithNoExt = path.removeSuffix(".$extension")

    fun buildPath(suffix: String) = "$pathWithNoExt$suffix${if (extension.isNotEmpty()) ".$extension" else ""}"

    val lightPath = buildPath(LIGHT_SUFFIX)
    val lightHeaderPath = buildPath(LIGHT_HEADER_SUFFIX)
    val darkPath = buildPath(DARK_SUFFIX)
    val isDark = ColorUtil.isDark(JBUI.CurrentTheme.GotItTooltip.background(false))
    return when {
      isDark && StartupUiUtil.isUnderDarcula -> listOf(darkPath, lightPath, lightHeaderPath)  // Dark theme
      isDark -> listOf(lightPath, lightHeaderPath, darkPath)                                  // Light theme
      else -> listOf(lightHeaderPath, lightPath, darkPath)                                    // Light with light header theme
    }
  }

  private fun readResource(path: String, classLoader: ClassLoader): String? {
    return try {
      classLoader.getResource(path)?.readText()
    }
    catch (_: Throwable) {
      null
    }
  }

  private fun getLottieImageSize(lottieJson: String): Dimension? {
    return try {
      WebAnimationUtils.getLottieImageSize(lottieJson)
    }
    catch (t: Throwable) {
      LOG.error("Failed to parse lottie json", t)
      null
    }
  }

  private val LOG = thisLogger()
}