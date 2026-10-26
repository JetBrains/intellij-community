// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * The [default lines][AnalysisIgnoreDefaults.lines] that the user edits on the [Default Exclusions][AnalysisIgnoreDefaultsConfigurable]
 * page. The state holds no lines while the user keeps the [built-in lines][AnalysisIgnoreDefaults.BUILT_IN_LINES], so that a later
 * product version can change them.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
@State(name = "AnalysisIgnoreDefaults", storages = [Storage("analysisIgnore.xml")], category = SettingsCategory.CODE)
class AnalysisIgnoreDefaultsSettings : SimplePersistentStateComponent<AnalysisIgnoreDefaultsSettings.LinesState>(LinesState()) {

  @Volatile
  private var currentLines: List<String> = AnalysisIgnoreDefaults.BUILT_IN_LINES

  /** The default lines in effect. The same list instance stays until the lines change. */
  val lines: List<String>
    get() = currentLines

  /** Returns `true` if the user replaced the [built-in lines][AnalysisIgnoreDefaults.BUILT_IN_LINES]. */
  val isCustomized: Boolean
    get() = state.customized

  /**
   * Stores the [supported][AnalysisIgnoreDefaults.isSupportedLine] lines of [lines], and tells each [AnalysisIgnoreDefaultsListener] if they
   * changed. The built-in lines in any order clear the state, because the settings panel sorts the lines.
   */
  fun setLines(lines: List<String>) {
    val newLines = lines.filter(AnalysisIgnoreDefaults::isSupportedLine)
    val customized = newLines.toSet() != AnalysisIgnoreDefaults.BUILT_IN_LINES.toSet()
    state.customized = customized
    state.lines = if (customized) newLines.toMutableList() else ArrayList()
    val effectiveLines = if (customized) newLines else AnalysisIgnoreDefaults.BUILT_IN_LINES
    if (effectiveLines == currentLines) return
    currentLines = effectiveLines
    ApplicationManager.getApplication().messageBus.syncPublisher(AnalysisIgnoreDefaultsListener.TOPIC).linesChanged()
  }

  override fun loadState(state: LinesState) {
    super.loadState(state)
    val oldLines = currentLines
    // The state file can hold a line that the user typed by hand. The default entities hold the supported lines only.
    currentLines = if (state.customized) state.lines.filter(AnalysisIgnoreDefaults::isSupportedLine) else AnalysisIgnoreDefaults.BUILT_IN_LINES
    // A settings sync loads a new state while the projects are open.
    if (currentLines != oldLines) {
      ApplicationManager.getApplication().messageBus.syncPublisher(AnalysisIgnoreDefaultsListener.TOPIC).linesChanged()
    }
  }

  class LinesState : BaseState() {
    // `true` if [lines] replace the built-in lines. The flag tells an empty list of the user from the built-in lines.
    var customized: Boolean by property(false)
    var lines: MutableList<String> by list()
  }

  companion object {
    @JvmStatic
    fun getInstance(): AnalysisIgnoreDefaultsSettings = ApplicationManager.getApplication().service()
  }
}

/** Gets a call when the user changes the [default lines][AnalysisIgnoreDefaultsSettings.lines]. */
@ApiStatus.Internal
fun interface AnalysisIgnoreDefaultsListener {
  fun linesChanged()

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<AnalysisIgnoreDefaultsListener> = Topic(AnalysisIgnoreDefaultsListener::class.java, Topic.BroadcastDirection.NONE)
  }
}
