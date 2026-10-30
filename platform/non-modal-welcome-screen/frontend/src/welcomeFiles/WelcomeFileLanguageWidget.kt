// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.frontend.welcomeFiles

import com.intellij.icons.AllIcons
import com.intellij.ide.scratch.LRUPopupBuilder
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupShowOptions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles.WelcomeFileLanguage
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles.WelcomeFilesApi
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles.WelcomeLanguageItem
import com.intellij.platform.project.projectId
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.event.MouseEvent
import javax.swing.Icon

private const val ID: String = "WelcomeFileLanguage"

internal class WelcomeFileLanguageWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = ID

  override fun getDisplayName(): String = NonModalWelcomeScreenBundle.message("welcome.file.language.widget.name")

  override fun isAvailable(project: Project): Boolean = WelcomeUtils.isWelcomeProject(project)

  override fun createWidget(project: Project, scope: CoroutineScope): StatusBarWidget = WelcomeFileLanguageWidget(project, scope)
}

/**
 * Shows the language of the selected Home file, and lets the user change it. The widget is hidden for other files.
 * The backend owns the language, so the widget gets it from [WelcomeFilesApi] and changes it through [WelcomeFilesApi].
 */
private class WelcomeFileLanguageWidget(project: Project, scope: CoroutineScope)
  : EditorBasedStatusBarPopup(project = project, isWriteableFileRequired = false, scope = scope) {
  private class FileLanguageState(val file: VirtualFile, val language: WelcomeFileLanguage, val languages: List<WelcomeLanguageItem>)

  @Volatile
  private var state: FileLanguageState? = null

  private var selectedFileJob: Job? = null

  override fun ID(): String = ID

  override fun createInstance(project: Project): StatusBarWidget = WelcomeFileLanguageWidget(project, scope)

  override fun install(statusBar: StatusBar) {
    super.install(statusBar)

    selectedFileJob?.cancel()
    selectedFileJob = scope.launch {
      statusBar.currentEditor.map { it?.file }.distinctUntilChanged().collectLatest { file ->
        state = null
        update()
        if (file != null) {
          collectLanguage(file)
        }
      }
    }.also { it.cancelOnDispose(this) }
  }

  /**
   * Keeps [state] equal to the language of [file]. A file that is not a Home file gets no language, so [state] stays null.
   */
  private suspend fun collectLanguage(file: VirtualFile) {
    val api = WelcomeFilesApi.getInstance()
    var languages: List<WelcomeLanguageItem>? = null
    api.fileLanguage(project.projectId(), file.rpcId()).collect { language ->
      val items = languages ?: api.getLanguages().also { languages = it }
      state = FileLanguageState(file, language, items)
      update()
    }
  }

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    val state = state?.takeIf { it.file == file } ?: return WidgetState.HIDDEN
    val name = state.language.displayName
    val widgetState = WidgetState(NonModalWelcomeScreenBundle.message("welcome.file.language.widget.tooltip", name), name, true)
    widgetState.icon = state.language.icon?.icon()
    return widgetState
  }

  override fun createPopup(context: DataContext): ListPopup? {
    val state = state ?: return null
    val projectId = project.projectId()
    val fileId = state.file.rpcId()
    return object : LRUPopupBuilder<WelcomeLanguageItem>(project, NonModalWelcomeScreenBundle.message("welcome.file.language.popup.title")) {
      override fun getDisplayName(item: WelcomeLanguageItem): String = item.displayName

      override fun getIcon(item: WelcomeLanguageItem): Icon = item.icon?.icon() ?: AllIcons.FileTypes.Any_type

      override fun getStorageId(item: WelcomeLanguageItem): String = item.id
    }
      .forValues(state.languages)
      .withComparator { o1, o2 -> StringUtil.naturalCompare(o1.displayName, o2.displayName) }
      .withExtraSpeedSearchNamer { it.extension }
      .withSelection(null)
      .onChosen { item ->
        scope.launch {
          WelcomeFilesApi.getInstance().changeLanguage(projectId, fileId, item.id)
        }
      }
      .buildPopup()
  }

  override fun showPopup(popup: ListPopup, e: MouseEvent) {
    popup.show(PopupShowOptions.aboveComponent(e.component))
  }
}
