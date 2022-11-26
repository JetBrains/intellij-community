// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.TestOnly

class SuggestedRefactoringProviderImpl(project: Project) : SuggestedRefactoringProvider {
  companion object {
    fun getInstance(project: Project): SuggestedRefactoringProviderImpl {
      return SuggestedRefactoringProvider.getInstance(project) as SuggestedRefactoringProviderImpl
    }
  }

  val availabilityIndicator = SuggestedRefactoringAvailabilityIndicator(project)
  private val changeCollector = SuggestedRefactoringChangeCollector(availabilityIndicator)
  private val listener: SuggestedRefactoringChangeListener

  val state: SuggestedRefactoringState?
    get() = changeCollector.state

  init {
    listener = SuggestedRefactoringChangeListener(project, changeCollector, project)
  }

  internal class Startup : ProjectPostStartupActivity {
    override suspend fun execute(project: Project) {
      if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
        getInstance(project)
      }
    }
  }

  override fun reset() {
    // we must also reset new identifiers otherwise declaration is considered new after inplace-rename
    // see https://youtrack.jetbrains.com/issue/IDEA-233185
    listener.reset(withNewIdentifiers = true)
  }

  fun undoToState(state: SuggestedRefactoringState, signatureRange: TextRange) {
    listener.undoToState(state, signatureRange)
    changeCollector.undoToState(state)
  }

  fun suppressForCurrentDeclaration() {
    listener.suppressForCurrentDeclaration()
  }

  @set:TestOnly
  var _amendStateInBackgroundEnabled: Boolean
    get() = changeCollector._amendStateInBackgroundEnabled
    set(value) { changeCollector._amendStateInBackgroundEnabled = value }
}
