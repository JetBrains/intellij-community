// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.auth

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.CustomPluginRepositoryService
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import java.net.URLConnection
import kotlin.streams.toList

interface PluginAuthSubscriber {
  fun pluginAuthCallback()
}

object PluginAuthService {
  private val subscribers = HashSet<PluginAuthSubscriber>()
  private val authServicesStatus = HashMap<PluginsAuthExtension, AuthStatus>()
  private val hostsAuthHeaders = HashMap<String, Map<String, String>?>()

  init {
    subscribe(ServiceManager.getService(CustomPluginRepositoryService::class.java))
  }

  @Synchronized
  fun subscribe(subscriber: PluginAuthSubscriber) {
    subscribers.add(subscriber)
  }

  @Synchronized
  fun unsubscribe(subscriber: PluginAuthSubscriber) {
    subscribers.remove(subscriber)
  }

  @Synchronized
  fun setAuthStatus(authService: PluginsAuthExtension, status: AuthStatus) {
    if (authServicesStatus[authService] != status) {
      authServicesStatus[authService] = status
      if (changeHostsState()) {
        subscribers.forEach { it.pluginAuthCallback() }
      }
    }
  }

  private fun changeHostsState(): Boolean {
    var result = false
    for (host in RepositoryHelper.getPluginHosts().filterNotNull()) {
      val headers = getUrlAuthHeaders(host)
      if (hostsAuthHeaders.containsKey(host).not() || hostsAuthHeaders[host] != headers) {
        hostsAuthHeaders[host] = headers
        result = true
      }
    }
    return result
  }

  fun getUrlAuthHeaders(url: String): Map<String, String>? {
    val services = PluginsAuthExtension.getAuthServices().filter { it.isUrlSupported(url) }.toList()
    if (services.size > 1) {
      UpdateChecker.getNotificationGroup().createNotification(
        IdeBundle.message("notification.title.custom.plugin.repository.auth"),
        IdeBundle.message("notification.content.there.are.too.many.authorization.services.supporting", url, services.map { it.name }.joinToString(",")),
        NotificationType.WARNING).notify(null)
      return null
    }
    return if (services.size == 1) {
      val service = services.first()
      if (service.initAuthIfNeeded("Custom plugin repository '$url'")) {
        service.generateAuthHeaders(url)
      } else {
        null
      }
    }
    else null
  }

  fun addAuthHeadersIfTheyExist(tuner: URLConnection, url: String){
    getUrlAuthHeaders(url)?.forEach { (K, V) ->tuner.addRequestProperty(K, V) }
  }
}