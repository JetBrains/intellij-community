// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.project

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.util.TimeoutUtil

class LightEditProjectManager {
  companion object {
    private val LOG = logger<LightEditProjectManager>()
    private val LOCK = Any()

    private fun fireProjectOpened(project: Project) {
      val app = ApplicationManager.getApplication()
      val fireRunnable = Runnable {
        // similar to com.intellij.openapi.project.impl.ProjectManagerExImplKt.openProject
        app.messageBus.syncPublisher(ProjectManager.TOPIC).projectOpened(project)
        runUnderModalProgressIfIsEdt {
          val startupManager = StartupManager.getInstance(project) as StartupManagerImpl
          startupManager.initProject()
          startupManager.runPostStartupActivities()
        }
      }
      if (app.isDispatchThread || app.isUnitTestMode) {
        fireRunnable.run()
      }
      else {
        // Initialize ActionManager out of EDT to pass "assert !app.isDispatchThread()" in ActionManagerImpl
        ActionManager.getInstance()
        app.invokeLater(fireRunnable)
      }
    }

    private fun createProject(): LightEditProjectImpl {
      val start = System.nanoTime()
      val project = LightEditProjectImpl()
      LOG.info(LightEditProjectImpl::class.java.simpleName + " loaded in " + TimeoutUtil.getDurationMillis(start) + " ms")
      return project
    }
  }

  @Volatile
  private var projectImpl: LightEditProjectImpl? = null

  val project: Project?
    get() = projectImpl

  fun getOrCreateProject(): Project {
    var project = projectImpl
    if (project == null) {
      var created = false
      synchronized(LOCK) {
        if (projectImpl == null) {
          projectImpl = createProject()
          created = true
        }
        project = projectImpl
      }
      if (created) {
        fireProjectOpened(project!!)
        ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
          override fun projectClosed(project: Project) {
            if (project === projectImpl) {
              synchronized(LOCK) { projectImpl = null }
            }
          }
        })
      }
    }
    return project!!
  }
}