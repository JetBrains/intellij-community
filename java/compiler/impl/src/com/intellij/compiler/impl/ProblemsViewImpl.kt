// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl

import com.intellij.compiler.ProblemsView
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.errorTreeView.ErrorTreeElementKind
import com.intellij.ide.errorTreeView.ErrorViewStructure
import com.intellij.ide.errorTreeView.GroupingElement
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.*
import com.intellij.openapi.compiler.CompileScope
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.pom.Navigatable
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.Icon
import kotlin.time.Duration.Companion.milliseconds

private const val AUTO_BUILD_TOOLWINDOW_ID = "Problems"

private val interestingMessageKinds = EnumSet.of(ErrorTreeElementKind.ERROR, ErrorTreeElementKind.WARNING, ErrorTreeElementKind.NOTE)

@OptIn(FlowPreview::class)
internal class ProblemsViewImpl(project: Project) : ProblemsView(project) {
  @Volatile
  private var panel: ProblemsViewPanel? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  private val messageScope = project.coroutineScope.childScope(Dispatchers.Default.limitedParallelism(1))

  private val state = NewErrorTreeViewPanel.MessageViewState()
  private val errorViewStructure = ErrorViewStructure(project, /* canHideWarnings = */ false)

  private val iconFlow = MutableStateFlow(AllIcons.Toolwindows.ProblemsEmpty)

  init {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    toolWindowManager.invokeLater(Runnable {
      val toolWindow = toolWindowManager.registerToolWindow(AUTO_BUILD_TOOLWINDOW_ID) {
        icon = getEffectiveIcon()
        stripeTitle = IdeBundle.messagePointer("toolwindow.stripe.Problems")
        canCloseContent = false
        @Suppress("ObjectLiteralToLambda")
        contentFactory = object : ToolWindowFactory {
          override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
            val panel = ProblemsViewPanel(project = project, state = state, errorViewStructure = errorViewStructure)
            Disposer.register(toolWindow.disposable, panel)
            this@ProblemsViewImpl.panel = panel

            val contentManager = toolWindow.contentManager
            val content = contentManager.factory.createContent(panel, "", false)
            content.helpId = "reference.problems.tool.window"
            contentManager.addContent(content)
          }
        }
      }

      project.coroutineScope.launch {
        iconFlow
          .debounce(100.milliseconds)
          .collectLatest { icon ->
            withContext(Dispatchers.EDT) {
              toolWindow.setIcon(icon)
            }
          }
      }
    })
  }

  private fun getEffectiveIcon(): Icon {
    return if (errorViewStructure.hasMessages(interestingMessageKinds)) {
      AllIcons.Toolwindows.Problems
    }
    else {
      AllIcons.Toolwindows.ProblemsEmpty
    }
  }

  override fun clearOldMessages(scope: CompileScope?, currentSessionId: UUID) {
    messageScope.launch {
      cleanupChildrenRecursively(errorViewStructure.rootElement, scope, currentSessionId)
      panel?.reload()
      iconFlow.value = getEffectiveIcon()
    }
  }

  override fun addMessage(type: Int,
                          text: Array<String>,
                          groupName: String?,
                          navigatable: Navigatable?,
                          exportTextPrefix: String?,
                          rendererTextPrefix: String?,
                          sessionId: UUID) {
    messageScope.launch {
      val structure = errorViewStructure
      val group = structure.lookupGroupingElement(groupName)
      if (group != null && sessionId != group.data) {
        structure.removeElement(group)
      }

      val kind = ErrorTreeElementKind.convertMessageFromCompilerErrorType(type)
      val element = if (navigatable == null) {
        errorViewStructure.addMessage(
          /* kind = */ kind,
          /* text = */ text,
          /* underFileGroup = */ null,
          /* file = */ null,
          /* line = */ -1,
          /* column = */ -1,
          /* data = */ sessionId
        )
      }
      else {
        errorViewStructure.addNavigatableMessage(
          /* groupName = */ groupName,
          /* navigatable = */ navigatable,
          /* kind = */ kind,
          /* message = */ text,
          /* data = */ sessionId,
          /* exportText = */ exportTextPrefix ?: "",
          /* rendererTextPrefix = */ rendererTextPrefix ?: "",
          /* file = */ (navigatable as? OpenFileDescriptor)?.file
        )
      }
      panel?.updateAddedElement(element)

      iconFlow.value = if (interestingMessageKinds.contains(kind) || errorViewStructure.hasMessages(interestingMessageKinds)) {
        AllIcons.Toolwindows.Problems
      }
      else {
        AllIcons.Toolwindows.ProblemsEmpty
      }
    }
  }

  override fun setProgress(text: @Nls String?, fraction: Float) {
    state.progressText = text
    state.fraction = fraction
    panel?.updateProgress()
  }

  override fun setProgress(text: @Nls String?) {
    state.progressText = text
    panel?.updateProgress()
  }

  override fun clearProgress() {
    state.clearProgress()
    panel?.updateProgress()
  }

  private fun cleanupChildrenRecursively(fromElement: Any, scope: CompileScope?, currentSessionId: UUID) {
    for (element in errorViewStructure.getChildElements(fromElement)) {
      if (element is GroupingElement) {
        if (scope != null) {
          val file = element.file
          if (file != null && !scope.belongs(file.url)) {
            continue
          }
        }
        if (currentSessionId != element.getData()) {
          errorViewStructure.removeElement(element)
        }
        else {
          cleanupChildrenRecursively(element, scope, currentSessionId)
        }
      }
      else if (currentSessionId != element.data) {
        errorViewStructure.removeElement(element)
      }
    }
  }
}

class ProblemsViewPanel(project: Project, state: MessageViewState, errorViewStructure: ErrorViewStructure?)
  : NewErrorTreeViewPanel(myProject = project,
                          helpId = null,
                          createExitAction = false,
                          state = state,
                          errorViewStructure = errorViewStructure) {
  constructor(project: Project) : this(project, MessageViewState(), null)

  init {
    myTree.emptyText.text = JavaCompilerBundle.message("no.compilation.problems.found")
    // hack: this will pre-initialize progress UI
    updateProgress()
  }

  override fun fillRightToolbarGroup(group: DefaultActionGroup) {
    super.fillRightToolbarGroup(group)

    group.addSeparator()
    group.add(CompilerPropertiesAction())
  }

  override fun addExtraPopupMenuActions(group: DefaultActionGroup) {
    group.add(object : ExcludeFromCompileAction(myProject) {
      override fun getFile() = selectedFile
    })
    // todo: do we need compiler's popup actions here?
    //ActionGroup popupGroup = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_COMPILER_ERROR_VIEW_POPUP);
    //if (popupGroup != null) {
    //  for (AnAction action : popupGroup.getChildren(null)) {
    //    group.add(action);
    //  }
    //}
  }

  override fun canHideWarnings() = false
}