package com.intellij.microservices.endpoints

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

interface EndpointsChangeTracker {
  fun setTrackingChanges(enabled: Boolean)

  companion object {

    @Topic.ProjectLevel
    val TOPIC: Topic<EndpointsChangeTracker> = Topic(EndpointsChangeTracker::class.java)

    /**
     * Runs process with disabled PSI changes tracking in opened Endpoints View.
     */
    fun withExpectedChanges(project: Project, runnable: Runnable) {
      withExpectedChanges(project) { runnable.run() }
    }

    /**
     * Coroutine friendly [withExpectedChanges]
     */
    inline fun withExpectedChanges(project: Project, block: () -> Unit) {
      val publisher = project.messageBus.syncPublisher(TOPIC)
      publisher.setTrackingChanges(false)
      try {
        block()
      } finally {
        publisher.setTrackingChanges(true)
      }
    }
  }
}