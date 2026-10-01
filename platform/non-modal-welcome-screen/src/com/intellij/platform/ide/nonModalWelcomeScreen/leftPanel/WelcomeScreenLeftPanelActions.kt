package com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel

import com.intellij.ide.IdeView
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.IdeViewForProjectViewPane
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenActionsUtil
import com.intellij.openapi.wm.impl.welcomeScreen.createToolWindowWelcomeScreenVerticalToolbar
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.recordStartupSpan
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenPaintTracker
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Graphics
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
class WelcomeScreenLeftPanelActions(val project: Project) {
  /**
   * Creates the toolbar of the left panel.
   *
   * When [ActionManager] is already created, the function fills the action group before it creates the toolbar.
   * Then the first toolbar update on show uses the synchronous fast track, and the first paint shows the actions.
   * Otherwise, a coroutine in [scope] awaits [ActionManager], fills the group, and updates the toolbar.
   *
   * The panel calls [onActionsPainted] once, on the first paint that shows the actions.
   */
  fun createButtonsComponent(scope: CoroutineScope, onActionsPainted: () -> Unit = {}): JComponent {
    val group = DefaultActionGroup()
    val actionManager = ApplicationManager.getApplication().serviceIfCreated<ActionManager>()
    if (actionManager != null) {
      // The toolbar is not in the component hierarchy yet, so its first update starts on show.
      // The span covers only the group fill.
      val fillStart = System.nanoTime()
      fillActionGroup(group, actionManager)
      recordStartupSpan(TOOLBAR_FIRST_FILL_SPAN, fillStart, System.nanoTime()) {
        it.setAttribute("synchronous", true)
      }
    }

    val toolbar = createToolWindowWelcomeScreenVerticalToolbar(group)

    if (actionManager == null) {
      scope.launch {
        val fillStart = System.nanoTime()
        fillActionGroup(group, serviceAsync<ActionManager>())

        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          val update = toolbar.updateActionsAsync()
          // The callback only records the span. The coroutine does not wait for the update.
          (update as? CompletableFuture<*>)?.whenComplete { _, error ->
            if (error == null) {
              val hasVisibleActions = toolbar.hasVisibleActions()
              recordStartupSpan(TOOLBAR_FIRST_FILL_SPAN, fillStart, System.nanoTime()) {
                it.setAttribute("synchronous", false)
                it.setAttribute("hasVisibleActions", hasVisibleActions)
              }
            }
          }
        }
      }
    }

    return WelcomeScreenLeftToolbarPanel(toolbar, WelcomeScreenPaintTracker.getInstance(project), onActionsPainted) { sink ->
      sink[WelcomeScreenActionsUtil.NON_MODAL_WELCOME_SCREEN] = true
      sink[CommonDataKeys.PROJECT] = project
      sink[LangDataKeys.IDE_VIEW] = getIdeView(project)
    }
  }

  private fun fillActionGroup(group: DefaultActionGroup, actionManager: ActionManager) {
    // TODO: Do something with the action group. Now it only is present in Rider
    val actions = actionManager.getAction("NonModalWelcomeScreen.LeftTabActions") as? DefaultActionGroup
    if (actions == null) {
      actionManager.getAction("WelcomeScreen.OpenDirectoryProject")?.let { group.add(it) }
      actionManager.getAction("NonModalWelcomeScreen.LeftTabActions.New.Action")?.let { group.add(it) }
      actionManager.getAction("Vcs.VcsClone")?.let { group.add(it) }
      actionManager.getAction("NonModalWelcomeScreen.RemoteDevelopmentActions")?.let { group.add(it) }
    }
    else {
      actions.childActionsOrStubs.forEach(group::add)
    }
  }

  /**
   * Needed for new file creation actions as part of the context
   */
  private fun getIdeView(project: Project): IdeView {
    val projectViewPane = ProjectView.getInstance(project).getCurrentProjectViewPane()
    val baseView = IdeViewForProjectViewPane(Supplier { projectViewPane })
    return object : IdeView {
      override fun getDirectories(): Array<PsiDirectory> {
        val elements = orChooseDirectory ?: return PsiDirectory.EMPTY_ARRAY
        return arrayOf(elements)
      }

      override fun getOrChooseDirectory(): PsiDirectory? {
        val projectDir = project.guessProjectDir() ?: return null
        return PsiManager.getInstance(project).findDirectory(projectDir)
      }

      override fun selectElement(element: PsiElement) {
        baseView.selectElement(element)
      }
    }
  }
}

private const val TOOLBAR_FIRST_FILL_SPAN = "welcome left toolbar first fill"

/**
 * Holds the toolbar of the left panel and gives its data.
 * The first paint that shows the actions calls [WelcomeScreenPaintTracker.leftPainted] and then [onActionsPainted].
 */
private class WelcomeScreenLeftToolbarPanel(
  private val toolbar: ActionToolbar,
  private val paintTracker: WelcomeScreenPaintTracker,
  private val onActionsPainted: () -> Unit,
  private val dataProvider: UiDataProvider,
) : JPanel(BorderLayout()), UiDataProvider {
  private var isActionsPainted = false

  init {
    add(toolbar.component, BorderLayout.CENTER)
    isOpaque = toolbar.component.isOpaque
  }

  override fun uiDataSnapshot(sink: DataSink) {
    DataSink.uiDataSnapshot(sink, dataProvider)
  }

  override fun paint(g: Graphics) {
    super.paint(g)
    if (!isActionsPainted && toolbar.hasVisibleActions()) {
      isActionsPainted = true
      paintTracker.leftPainted()
      onActionsPainted()
    }
  }
}
