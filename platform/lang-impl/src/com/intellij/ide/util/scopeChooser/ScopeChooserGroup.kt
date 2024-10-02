// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.scope.packageSet.NamedScopeManager
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.SwingUtilities

@ApiStatus.Internal
class ScopeChooserGroup(project: Project, parentDisposable: Disposable, initialScope: SearchScope?) : ActionGroup(), CustomComponentAction {

  private val scopeModel = project.service<ScopeService>().createModel(setOf(ScopeOption.FROM_SELECTION, ScopeOption.USAGE_VIEW))
  private var actions = arrayOf<AnAction>()
  private val changeListeners = mutableListOf<Consumer<SearchScope?>>()

  var selected: SearchScope? = null
    set(value) {
      if (field != value) {
        field = value

        @Suppress("DialogTitleCapitalization")
        templatePresentation.text = value?.displayName
        for (listener in changeListeners) {
          listener.accept(value)
        }
      }
    }

  private val scopeModelListener = object : ScopeModelListener {
    override fun scopesUpdated(scopes: ScopesSnapshot) {
      SwingUtilities.invokeLater {
        updateActions(scopes.scopeDescriptors)
      }
    }
  }

  init {
    isPopup = true
    selected = initialScope
    scopeModel.addScopeModelListener(scopeModelListener)
    Disposer.register(parentDisposable, scopeModel)

    val scopeListener = NamedScopesHolder.ScopeListener { updateActions() }
    NamedScopeManager.getInstance(project).addScopeListener(scopeListener, parentDisposable)
    DependencyValidationManager.getInstance(project).addScopeListener(scopeListener, parentDisposable)

    updateActions()
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return actions
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun actionPerformed(event: AnActionEvent) {
        HelpTooltip.hide(this)
        showActionGroupPopup(this@ScopeChooserGroup, event)
      }
    }
  }

  fun addChangeListener(listener: Consumer<SearchScope?>) {
    changeListeners += listener
  }

  private fun updateActions() {
    scopeModel.refreshScopes()
  }

  @RequiresEdt
  private fun updateActions(descriptors: List<ScopeDescriptor>) {
    actions = descriptors.map { descriptor ->
      if (descriptor is ScopeSeparator)
        Separator(descriptor.text)
      else
        DumbAwareAction.create(descriptor.displayName, descriptor.icon) {
          selected = descriptor.scope
        }
    }.toTypedArray()
  }
}
