// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.frontend.leftPanel

import com.intellij.ide.SelectInTarget
import com.intellij.ide.rpc.getComponent
import com.intellij.ide.rpc.getFocusTargetForTransferredComponent
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import com.intellij.platform.projectView.frontend.pane.RestorePaneStateOptions
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorBuilder
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jdom.Element
import java.awt.BorderLayout
import javax.swing.JComponent

internal class FrontendWelcomeScreenLeftPanelProvider : PureUiProjectViewPaneProvider {
  override fun getPaneModelsFlow(project: Project): Flow<Collection<FrontendProjectViewPaneModel>> {
    return flow {
      if (project.isWelcomeExperienceProject() && isNonModalWelcomeScreenEnabled) {
        emit(listOf(FrontendWelcomeScreenLeftPanelModel(project)))
      }
      else {
        // Do not skip emit here, otherwise `combine` in
        // `PureUiProjectViewPaneService` will not call its `transform` block
        emit(emptyList())
      }
    }
  }
}

private class FrontendWelcomeScreenLeftPanelModel(private val project: Project) : FrontendProjectViewPaneModel {
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
    return project.service<FrontendWelcomeScreenLeftPanelCreator>().createPane(descriptor)
  }
}

@Service(Service.Level.PROJECT)
private class FrontendWelcomeScreenLeftPanelCreator(
  private val project: Project,
  private val scope: CoroutineScope
) {
  fun createPane(descriptor: ProjectViewPaneDescriptor): FrontendProjectViewPane {
    val deferred = scope.async(Dispatchers.Default) {
      val componentId = WelcomeScreenLeftPanelRpc.getInstance().getComponentId(project.projectId())
      withContext(Dispatchers.UI) {
        componentId.getComponent()
      }
    }
    return FrontendWelcomeScreenLeftPanel(descriptor, deferred, scope)
  }
}

internal class FrontendWelcomeScreenLeftPanel(
  override val descriptor: ProjectViewPaneDescriptor,
  private val deferredContent: Deferred<JComponent?>,
  scope: CoroutineScope,
) : FrontendProjectViewPane {
  override val component = JBLoadingPanel(BorderLayout(), scope.asDisposable())

  @OptIn(ExperimentalCoroutinesApi::class)
  override val componentToFocus: JComponent
    get() = runCatching {
      deferredContent.getCompleted()?.let {
        getFocusTargetForTransferredComponent(it)
      }
    }.getOrNull() ?: component

  init {
    scope.launch(Dispatchers.UI) {
      component.startLoading()
      val content = try {
        deferredContent.await()
      }
      finally {
        component.stopLoading()
      }
      if (content == null) {
        return@launch
      }

      component.add(content, BorderLayout.CENTER)
      if (component.isFocusOwner) {
        getFocusTargetForTransferredComponent(content).requestFocusInWindow()
      }
    }
  }

  override var isCurrent: Boolean = false

  override val selectInTargets: Collection<SelectInTarget> = listOf(WelcomeScreenLeftPanelSelectInTarget())

  override suspend fun manage() { }

  override fun saveStateTo(element: Element): Boolean {
    return false
  }

  override fun restoreStateFrom(element: Element?, options: RestorePaneStateOptions) { }
}
