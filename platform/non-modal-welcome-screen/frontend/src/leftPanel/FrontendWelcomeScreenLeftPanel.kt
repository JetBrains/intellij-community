// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.frontend.leftPanel

import com.intellij.ide.SelectInTarget
import com.intellij.ide.rpc.getComponent
import com.intellij.openapi.application.UI
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.isNonModalWelcomeScreenEnabled
import com.intellij.platform.ide.nonModalWelcomeScreen.isWelcomeExperienceProject
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftPanel
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftPanelRpc
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftPanelSelectInTarget
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPane
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneModel
import com.intellij.platform.projectView.frontend.pane.PureUiProjectViewPaneProvider
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorBuilder
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.ui.SearchTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.jdom.Element
import javax.swing.JComponent

internal class FrontendWelcomeScreenLeftPanelProvider : PureUiProjectViewPaneProvider {
  override fun getPaneModelsFlow(project: Project): Flow<Collection<FrontendProjectViewPaneModel>> {
    return flow {
      if (project.isWelcomeExperienceProject() && isNonModalWelcomeScreenEnabled) {
        val component = withContext(Dispatchers.UI) {
          WelcomeScreenLeftPanelRpc.getInstance().getComponentId(project.projectId()).getComponent()
        }
        if (component == null) {
          emit(emptyList())
        }
        else {
          emit(listOf(FrontendWelcomeScreenLeftPanelModel(project, component)))
        }
      }
    }
  }
}

private class FrontendWelcomeScreenLeftPanelModel(private val project: Project, private val component: JComponent) : FrontendProjectViewPaneModel {
  override suspend fun describe(builder: ProjectViewPaneDescriptorBuilder): ProjectViewPaneDescriptor {
    builder.setDefault(project.isWelcomeExperienceProject())
    builder.setIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Folder))
    return builder.build(
      id = projectViewPaneId(WelcomeScreenLeftPanel.ID),
      presentableName = NonModalWelcomeScreenBundle.message("welcome.screen.project.view.title"),
      order = -10,
    )
  }

  override fun createPane(descriptor: ProjectViewPaneDescriptor): FrontendProjectViewPane {
    return FrontendWelcomeScreenLeftPanel(descriptor, component)
  }
}

internal class FrontendWelcomeScreenLeftPanel(
  override val descriptor: ProjectViewPaneDescriptor,
  override val component: JComponent,
) : FrontendProjectViewPane {
  private lateinit var searchField: SearchTextField

  override val componentToFocus: JComponent
    get() = searchField

  override var isCurrent: Boolean = false

  override val selectInTargets: Collection<SelectInTarget> = listOf(WelcomeScreenLeftPanelSelectInTarget())

  override suspend fun manage() { }

  override fun saveStateTo(element: Element) { }

  override fun restoreStateFrom(element: Element?) { }
}
