// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.update

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent

@Internal
fun <T> lazyUiDisposable(parent: Disposable?, ui: JComponent, child: T, task: (child: T, project: Project?) -> Unit) {
  val uiRef = AtomicReference(ui)
  UiNotifyConnector.Once.installOn(ui, object  : Activatable {
    override fun showNotify() {
      @Suppress("NAME_SHADOWING")
      val ui = uiRef.getAndSet(null) ?: return
      var project: Project? = null
      @Suppress("NAME_SHADOWING")
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
          logger<Disposable>().warn("use application as a parent disposable")
          ApplicationManager.getApplication()
        }
        else {
          logger<Disposable>().warn("use project as a parent disposable")
          project
        }
      }
      task(child, project)
      if (child is Disposable) {
        Disposer.register(parent!!, child)
      }
    }
  })
}