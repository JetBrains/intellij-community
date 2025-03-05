package com.intellij.microservices.endpoints

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

interface EndpointsViewListener {
  enum class ChangeType {
    FLUSH,
    PROVIDERS,
    ITEMS,
    VIEW
  }

  class ChangeEvent(val project: Project,
                    val type: ChangeType)

  fun endpointsChanged(e: ChangeEvent)

  companion object {

    @Topic.ProjectLevel
    val TOPIC: Topic<EndpointsViewListener> = Topic(EndpointsViewListener::class.java)
  }
}