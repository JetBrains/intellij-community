// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.util.TipDialog
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.EdtScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class TipOfTheDayStartupActivity : StartupActivity.DumbAware {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment || PlatformUtils.isRider() || !GeneralSettings.getInstance().isShowTipsOnStartup) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun runActivity(project: Project) {
    val disposableRef = AtomicReference<Disposable?>()
    val future = EdtScheduledExecutorService.getInstance().schedule({
      val disposable = disposableRef.getAndSet(null) ?: return@schedule
      Disposer.dispose(disposable)

      if (!project.isDisposed && TipDialog.canBeShownAutomaticallyNow(project)) {
        TipsOfTheDayUsagesCollector.triggerDialogShown(TipsOfTheDayUsagesCollector.DialogType.automatically)
        TipDialog.showForProject(project)
      }
    }, 5, TimeUnit.SECONDS)

    val disposable = Disposable {
      disposableRef.set(null)
      future.cancel(false)
    }
    disposableRef.set(disposable)
    Disposer.register(project, disposable)
  }
}