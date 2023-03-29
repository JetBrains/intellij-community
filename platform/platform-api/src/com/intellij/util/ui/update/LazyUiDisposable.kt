// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.update

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent

@ApiStatus.Internal
abstract class LazyUiDisposable<T>(private val parent: Disposable?, ui: JComponent, private val child: T) : Activatable {
  private val uiRef: AtomicReference<JComponent?>

  init {
    uiRef = AtomicReference(ui)
    UiNotifyConnector.Once.installOn(ui, this)
  }

  override fun showNotify() {
    val ui = uiRef.getAndSet(null) ?: return
    var project: Project? = null
    var parent = parent
    if (ApplicationManager.getApplication() != null) {
      val context = DataManager.getInstance().getDataContext(ui)
      project = CommonDataKeys.PROJECT.getData(context)
      if (parent == null) {
        parent = PlatformDataKeys.UI_DISPOSABLE.getData(context)
      }
    }
    if (parent == null) {
      parent = if (project == null) {
        Logger.getInstance(LazyUiDisposable::class.java).warn("use application as a parent disposable")
        ApplicationManager.getApplication()
      }
      else {
        Logger.getInstance(LazyUiDisposable::class.java).warn("use project as a parent disposable")
        project
      }
    }
    initialize(parent!!, child, project)
    if (child is Disposable) {
      Disposer.register(parent, (child as Disposable))
    }
  }

  protected abstract fun initialize(parent: Disposable, child: T, project: Project?)
}