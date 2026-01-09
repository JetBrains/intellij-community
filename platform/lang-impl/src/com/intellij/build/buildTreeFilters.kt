// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("BuildTreeFilters")

package com.intellij.build

import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import java.util.function.Predicate
import java.util.function.Supplier

@ApiStatus.Internal
val SUCCESSFUL_STEPS_FILTER: Predicate<ExecutionNode> = Predicate { node: ExecutionNode -> !node.isFailed && !node.hasWarnings() }
@ApiStatus.Internal
val WARNINGS_FILTER: Predicate<ExecutionNode> = Predicate { node: ExecutionNode -> node.hasWarnings() || node.hasInfos() }

@ApiStatus.Internal
class WeakFilterableSupplier<T>(t: T) : Supplier<T?> {
  private val t = WeakReference(t)

  override fun get(): T? {
    return t.get()
  }
}

@ApiStatus.Experimental
fun createFilteringActionsGroup(filterable: Supplier<Filterable<ExecutionNode>?>): DefaultActionGroup {
  val actionGroup = DefaultActionGroup(LangBundle.message("action.filters.text"), true)
  actionGroup.templatePresentation.icon = AllIcons.Actions.Show
  actionGroup.add(WarningsToggleAction(filterable))
  actionGroup.add(SuccessfulStepsToggleAction(filterable))
  return actionGroup
}

@ApiStatus.Experimental
internal fun install(filterable: Filterable<ExecutionNode>) {
  if (!filterable.isFilteringEnabled) return

  SuccessfulStepsToggleAction.install(filterable)
  WarningsToggleAction.install(filterable)
}

@ApiStatus.Internal
open class FilterToggleAction(
  @NlsContexts.Command text: String,
  private val stateKey: String?,
  private val filterableRef: Supplier<Filterable<ExecutionNode>?>,
  private val filter: Predicate<ExecutionNode>,
  private val defaultState: Boolean
) : ToggleAction(text), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean {
    val filterable = filterableRef.get() ?: return false

    val presentation = e.presentation
    val filteringEnabled = filterable.isFilteringEnabled
    presentation.isEnabledAndVisible = filteringEnabled
    if (filteringEnabled && stateKey != null &&
        PropertiesComponent.getInstance().getBoolean(stateKey, defaultState) &&
        !filterable.contains(filter)) {
      setSelected(e, true)
    }

    return filterable.contains(filter)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val filterable = filterableRef.get() ?: return

    if (state) {
      filterable.addFilter(filter)
    }
    else {
      filterable.removeFilter(filter)
    }
    if (stateKey != null) {
      PropertiesComponent.getInstance().setValue(stateKey, state, defaultState)
    }
  }
}

private fun install(
  filterable: Filterable<ExecutionNode>,
  filter: Predicate<ExecutionNode>,
  stateKey: String,
  defaultState: Boolean,
) {
  if (PropertiesComponent.getInstance().getBoolean(stateKey, defaultState) &&
      !filterable.contains(filter)) {
    filterable.addFilter(filter)
  }
}

@ApiStatus.Internal
class SuccessfulStepsToggleAction(filterable: Supplier<Filterable<ExecutionNode>?>) :
  FilterToggleAction(LangBundle.message("build.tree.filters.show.successful"),
                     STATE_KEY, filterable, SUCCESSFUL_STEPS_FILTER, DEFAULT_STATE), DumbAware {

  companion object {
    @ApiStatus.Internal
    const val STATE_KEY: String = "build.toolwindow.show.successful.steps.selection.state"
    @ApiStatus.Internal
    const val DEFAULT_STATE: Boolean = false

    fun install(filterable: Filterable<ExecutionNode>) {
      install(filterable, SUCCESSFUL_STEPS_FILTER, STATE_KEY, DEFAULT_STATE)
    }
  }
}

@ApiStatus.Internal
class WarningsToggleAction(filterable: Supplier<Filterable<ExecutionNode>?>) :
  FilterToggleAction(LangBundle.message("build.tree.filters.show.warnings"),
                     STATE_KEY, filterable, WARNINGS_FILTER, DEFAULT_STATE), DumbAware {

  companion object {
    @ApiStatus.Internal
    const val STATE_KEY: String = "build.toolwindow.show.warnings.selection.state"
    @ApiStatus.Internal
    const val DEFAULT_STATE: Boolean = true

    fun install(filterable: Filterable<ExecutionNode>) {
      install(filterable, WARNINGS_FILTER, STATE_KEY, DEFAULT_STATE)
    }
  }
}
