// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.ide.trustedProjects.impl.UntrustedProjectEditorNotificationPanel
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.LocatedProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

/**
 * Listens to the change of the project trusted state, i.e. when a non-trusted project becomes trusted (the vice versa is not possible).
 *
 * Consider using the helper method [onceWhenProjectTrusted] which accepts a lambda.
 */
@ApiStatus.Experimental
interface TrustedProjectsListener {

  /**
   * Executed when the project becomes trusted.
   */
  fun onProjectTrusted(locatedProject: LocatedProject) {
    locatedProject.project?.let { onProjectTrusted(it) }
  }

  /**
   * Executed when the project becomes in safe mode.
   */
  fun onProjectUntrusted(locatedProject: LocatedProject) {
    locatedProject.project?.let { onProjectUntrusted(it) }
  }

  /**
   * Executed when the project becomes trusted.
   */
  fun onProjectTrusted(project: Project) {
  }

  /**
   * Executed when the project becomes in safe mode.
   */
  fun onProjectUntrusted(project: Project) {
  }

  /**
   * Executed when the user clicks to the "Trust Project" button in the [editor notification][UntrustedProjectEditorNotificationPanel].
   * Use this method if you need to know that the project has become trusted exactly because the user has clicked to that button.
   *
   * NB: [onProjectTrusted] is also called in this case, and most probably you want to use that method.
   */
  fun onProjectTrustedFromNotification(project: Project) {
  }

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<TrustedProjectsListener> = Topic(TrustedProjectsListener::class.java, Topic.BroadcastDirection.NONE)

    /**
     * Adds a one-time listener of the project's trust state change: when the project becomes trusted, the listener is called and disconnected.
     */
    @JvmStatic
    @JvmOverloads
    fun onceWhenProjectTrusted(parentDisposable: Disposable? = null, listener: (Project) -> Unit) {
      val messageBus = ApplicationManager.getApplication().messageBus
      val connection = if (parentDisposable == null) messageBus.connect() else messageBus.connect(parentDisposable)
      connection.subscribe(TOPIC, object : TrustedProjectsListener {
        override fun onProjectTrusted(project: Project) {
          listener(project)
          connection.disconnect()
        }
      })
    }

    @JvmStatic
    @JvmOverloads
    fun onceWhenProjectTrusted(parentDisposable: Disposable? = null, listener: Consumer<Project>): Unit =
      onceWhenProjectTrusted(parentDisposable, listener::accept)
  }
}