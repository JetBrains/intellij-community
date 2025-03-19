// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.tabs.JBEditorTabsBase
import com.intellij.ui.tabs.JBTabsPresentation
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

open class JBEditorTabs : JBTabsImpl, JBEditorTabsBase {
  companion object {
    @JvmField
    val MARK_MODIFIED_KEY: Key<Boolean> = Key.create("EDITOR_TABS_MARK_MODIFIED")
  }

  private var isAlphabeticalModeChanged = false

  @Suppress("UNUSED_PARAMETER")
  constructor(project: Project?, focusManager: IdeFocusManager?, parentDisposable: Disposable)
    : super(
    project = project,
    parentDisposable = parentDisposable,
    coroutineScope = null,
    tabListOptions = TabListOptions(supportCompression = true),
  )

  constructor(project: Project?, parentDisposable: Disposable) : super(
    project = project,
    parentDisposable = parentDisposable,
    coroutineScope = null,
    tabListOptions = TabListOptions(supportCompression = true),
  )

  @Internal
  constructor(
    project: Project?,
    parentDisposable: Disposable,
    coroutineScope: CoroutineScope,
    tabListOptions: TabListOptions,
  ) : super(
    project = project,
    parentDisposable = parentDisposable,
    coroutineScope = coroutineScope,
    tabListOptions = tabListOptions,
  )

  override fun uiSettingsChanged(uiSettings: UISettings) {
    resetTabsCache()
    relayout(forced = true, layoutNow = false)

    super.uiSettingsChanged(uiSettings)
  }

  @Suppress("UNUSED_PARAMETER")
  @Deprecated("Use {@link #JBEditorTabs(Project, Disposable)}", level = DeprecationLevel.ERROR)
  constructor(project: Project?, actionManager: ActionManager, focusManager: IdeFocusManager?, parent: Disposable) : this(project, parent)

  override val isEditorTabs: Boolean
    get() = true

  override fun useSmallLabels(): Boolean = !ExperimentalUI.isNewUI() && UISettings.getInstance().useSmallLabelsOnTabs

  override fun isAlphabeticalMode(): Boolean {
    return if (isAlphabeticalModeChanged) super.isAlphabeticalMode() else UISettings.getInstance().sortTabsAlphabetically
  }

  override fun setAlphabeticalMode(alphabeticalMode: Boolean): JBTabsPresentation {
    isAlphabeticalModeChanged = true
    return super.setAlphabeticalMode(alphabeticalMode)
  }

  open fun shouldPaintBottomBorder(): Boolean = true
}
