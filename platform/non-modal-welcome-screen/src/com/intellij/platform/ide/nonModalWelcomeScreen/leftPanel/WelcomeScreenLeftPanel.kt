package com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel

import com.intellij.icons.AllIcons.Actions.Search
import com.intellij.ide.SelectInTarget
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider.Companion.isWelcomeScreenProject
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProjectCollectors
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectFilteringTree
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectPanelComponentFactory
import com.intellij.platform.ide.nonModalWelcomeScreen.GoFileDragAndDropHandler
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.isNonModalWelcomeScreenEnabled
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabVirtualFile
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

@ApiStatus.Internal
class WelcomeScreenLeftPanel(private val project: Project) : ProjectViewPane(project) {
  private var searchField: SearchTextField? = null

  override fun getTitle(): String = NonModalWelcomeScreenBundle.message("welcome.screen.project.view.title")

  override fun getId(): String = ID

  override fun getIcon(): Icon = IconManager.getInstance().getPlatformIcon(PlatformIcons.Folder)

  override fun isInitiallyVisible(): Boolean = isWelcomeScreenProject(project) && isNonModalWelcomeScreenEnabled

  override fun isDefaultPane(project: Project): Boolean {
    return !Registry.`is`("ide.welcome.screen.change.project.view.depending.on.opened.file", false) && isWelcomeScreenProject(project)
  }

  override fun getWeight(): Int = -10 // TODO: Increase weight?

  override fun createSelectInTarget(): SelectInTarget = WelcomeScreenLeftPanelSelectInTarget()

  private fun setupDragAndDrop(component: JComponent) {
    val target = object : DnDNativeTarget {
      override fun update(event: DnDEvent): Boolean {
        if (!FileCopyPasteUtil.isFileListFlavorAvailable(event)) {
          return false
        }
        event.isDropPossible = true
        return false
      }

      override fun drop(event: DnDEvent) {
        val files = FileCopyPasteUtil.getFileListFromAttachedObject(event.attachedObject)
          .map { file -> file.toPath() }
        GoFileDragAndDropHandler.openFiles(project, files)
      }
    }

    DnDSupport.createBuilder(component)
      .enableAsNativeTarget()
      .setTargetChecker(target)
      .setDropHandler(target)
      .setDisposableParent(this)
      .install()
  }

  override fun createComponent(): JComponent {

    val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
      border = JBUI.Borders.empty()
    }

    val projectFilteringTree = createRecentProjectTree()
    setupDragAndDrop(projectFilteringTree.component)

    val topPanel = JBPanel<JBPanel<*>>().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = JBUI.Borders.empty()
    }
    topPanel.add(WelcomeScreenLeftPanelActions(project).createButtonsComponent())
    topPanel.add(separator { customize(UnscaledGapsY(top = 17)) })
    topPanel.add(searchPanel(projectFilteringTree))
    topPanel.add(separator())

    mainPanel.add(topPanel, BorderLayout.NORTH)
    mainPanel.add(createScrollPane(projectFilteringTree.component, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER),
                  BorderLayout.CENTER)

    return mainPanel
  }

  override fun getComponentToFocus(): JComponent? {
    return searchField
  }

  override fun dispose() {
    searchField = null
    super.dispose()
  }

  private fun searchPanel(recentProjectTree: RecentProjectFilteringTree) = panel {
    row {
      val projectSearch = createProjectSearchField(recentProjectTree)
      searchField = projectSearch
      cell(projectSearch)
        .align(AlignX.FILL)
        .customize(UnscaledGaps(top = 4, bottom = 4, left = 20, right = 20))
    }
  }

  private fun createRecentProjectTree(): RecentProjectFilteringTree =
    RecentProjectPanelComponentFactory.createComponent(
      this,
      collectors = listOf(ProjectCollectors.cloneableProjectsCollector, ProjectCollectors.createRecentProjectsWithoutCurrentCollector(project)),
      treeBackground = null
    ).apply {
      tree.emptyText.text = NonModalWelcomeScreenBundle.message("welcome.screen.no.recent.projects")
      selectLastOpenedProjectOrTheFirstInTree()
    }

  private fun createProjectSearchField(recentProjectTree: RecentProjectFilteringTree): SearchTextField =
    recentProjectTree.installSearchField().apply {
      if (ExperimentalUI.isNewUI()) {
        textEditor.putClientProperty("JTextField.Search.Icon", Search)
      }
    }

  private fun separator(customize: Row.() -> Unit = {}) = panel {
    separator().customize()
  }

  companion object {
    const val ID = "NonModalWelcomeScreenProjectPane"
  }
}