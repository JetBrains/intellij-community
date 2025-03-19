package com.intellij.execution.multilaunch.servicesView

import com.intellij.execution.services.ServiceEventListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.execution.multilaunch.MultiLaunchConfiguration

@Service(Service.Level.PROJECT)
class MultiLaunchServicesRefresher(project: Project) {
  companion object {
    fun getInstance(project: Project) = project.service<MultiLaunchServicesRefresher>()
  }

  private val notifier = project.messageBus.syncPublisher(ServiceEventListener.TOPIC)

  fun refresh() {
    val event = ServiceEventListener.ServiceEvent.createResetEvent(MultiLaunchServiceContributor::class.java)
    notifier.handle(event)
  }

  fun refresh(configuration: MultiLaunchConfiguration) {
    val event = ServiceEventListener.ServiceEvent.createEvent(
      ServiceEventListener.EventType.SERVICE_STRUCTURE_CHANGED,
      configuration,
      MultiLaunchServiceContributor::class.java)
    notifier.handle(event)
  }
}