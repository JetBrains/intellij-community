// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.frontend.leftPanel

import com.intellij.icons.AllIcons.Actions.Search
import com.intellij.ide.SelectInTarget
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProjectCollectors
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectFilteringTree
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectPanelComponentFactory
import com.intellij.platform.ide.nonModalWelcomeScreen.DefaultFileDragAndDropHandler
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.isNonModalWelcomeScreenEnabled
import com.intellij.platform.ide.nonModalWelcomeScreen.isWelcomeExperienceProject
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WELCOME_SCREEN_IS_SHOWN
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftPanel
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftPanelActions
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftPanelSelectInTarget
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneModel
import com.intellij.platform.projectView.frontend.pane.PureUiProjectViewPaneProvider
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorBuilder
import com.intellij.platform.projectView.pane.projectViewPaneId
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
import com.intellij.util.asDisposable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jdom.Element
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

internal class FrontendWelcomeScreenLeftPanelProvider : PureUiProjectViewPaneProvider {
  override fun getPaneModelsFlow(project: Project): Flow<Collection<FrontendProjectViewPaneModel>> {
    return flow {
      if (project.isWelcomeExperienceProject() && isNonModalWelcomeScreenEnabled) {
        emit(listOf(FrontendWelcomeScreenLeftPanelModel(project)))
      }
    }
  }
}

private class FrontendWelcomeScreenLeftPanelModel(private val project: Project) : FrontendProjectViewPaneModel {
  override suspend fun describe(builder: ProjectViewPaneDescriptorBuilder): ProjectViewPaneDescriptor {
    builder.setDefault(
      !Registry.`is`("ide.welcome.screen.change.project.view.depending.on.opened.file", false) &&
      project.isWelcomeExperienceProject()
    )
    builder.setIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Folder))
    return builder.build(
      id = projectViewPaneId(WelcomeScreenLeftPanel.ID),
      presentableName = NonModalWelcomeScreenBundle.message("welcome.screen.project.view.title"),
      order = -10,
    )
  }

  override fun createPane(descriptor: ProjectViewPaneDescriptor): FrontendProjectViewPane {
    return FrontendWelcomeScreenLeftPanelService.getInstance(project).createPane(descriptor)
  }
}

@Service(Level.PROJECT)
internal class FrontendWelcomeScreenLeftPanelService(private val project: Project, private val scope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): FrontendWelcomeScreenLeftPanelService = project.service()
  }

  fun createPane(descriptor: ProjectViewPaneDescriptor): FrontendWelcomeScreenLeftPanel {
    return FrontendWelcomeScreenLeftPanel(project, scope, descriptor)
  }
}

internal class FrontendWelcomeScreenLeftPanel(
  private val project: Project,
  private val scope: CoroutineScope,
  override val descriptor: ProjectViewPaneDescriptor,
) : FrontendProjectViewPane {
  private lateinit var searchField: SearchTextField

  override val component: JComponent by lazy { createComponent() }

  override val componentToFocus: JComponent
    get() = searchField

  override var isCurrent: Boolean = false

  override val selectInTargets: Collection<SelectInTarget> = listOf(WelcomeScreenLeftPanelSelectInTarget())

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
        val handler = WelcomeRightTabContentProvider.getSingleExtension()?.getFileDragAndDropHandler()
                      ?: DefaultFileDragAndDropHandler
        handler.openFiles(project, files)
      }
    }

    DnDSupport.createBuilder(component)
      .enableAsNativeTarget()
      .setTargetChecker(target)
      .setDropHandler(target)
      .setDisposableParent(scope.asDisposable())
      .install()
  }

  private fun createComponent(): JComponent {
    val mainPanel = MyMainPanel()

    val projectFilteringTree = createRecentProjectTree()
    setupDragAndDrop(projectFilteringTree.component)

    val topPanel = JBPanel<JBPanel<*>>().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = JBUI.Borders.empty()
    }
    topPanel.add(WelcomeScreenLeftPanelActions(project).createButtonsComponent(scope))
    topPanel.add(separator { customize(UnscaledGapsY(top = 17)) })
    topPanel.add(searchPanel(projectFilteringTree))
    topPanel.add(separator())

    mainPanel.add(topPanel, BorderLayout.NORTH)
    mainPanel.add(createScrollPane(projectFilteringTree.component, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER),
                  BorderLayout.CENTER)

    return mainPanel
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
      scope.asDisposable(),
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

  override suspend fun manage() { }

  override fun saveStateTo(element: Element) { }

  override fun restoreStateFrom(element: Element) { }

  private class MyMainPanel : JBPanel<JBPanel<*>>(BorderLayout()), UiDataProvider {
    init {
      border = JBUI.Borders.empty()
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[WELCOME_SCREEN_IS_SHOWN] = true
    }
  }
}
