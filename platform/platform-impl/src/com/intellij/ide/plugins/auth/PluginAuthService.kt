// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.auth

import com.intellij.ide.plugins.CustomPluginRepositoryService
import com.intellij.ide.plugins.RepositoryHelper.getPluginHosts
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.ServiceManager
import java.net.URLConnection

interface PluginAuthSubscriber {
  fun pluginAuthCallback()
}

object PluginAuthService {
  private val subscribers = HashSet<PluginAuthSubscriber>()
  private val authServices = HashSet<AuthService>()
  private val authServicesStatus = HashMap<AuthService, AuthStatus>()
  private val hostsAuthHeaders = HashMap<String, Map<String, String>?>()

  init {
    getPluginHosts().filterNotNull().forEach { hostsAuthHeaders[it] = null }
    subscribe(ServiceManager.getService(CustomPluginRepositoryService::class.java))
  }

  @Synchronized
  fun register(authService: AuthService) {
    authServices.add(authService)
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
  fun setAuthStatus(authService: AuthService, status: AuthStatus) {
    if (authServices.contains(authService) && authServicesStatus[authService] != status) {
      authServicesStatus[authService] = status
      if (changeHostsState()) {
        subscribers.forEach { it.pluginAuthCallback() }
      }
    }
  }

  private fun changeHostsState(): Boolean {
    var result = false
    for (host in getPluginHosts().filterNotNull()) {
      val headers = getUrlAuthHeaders(host)
      if (!hostsAuthHeaders.containsKey(host) || hostsAuthHeaders[host] != headers) {
        hostsAuthHeaders[host] = headers
        result = true
      }
    }
    return result
  }

  fun getUrlAuthHeaders(url: String): Map<String, String>? {
    val services = authServices.filter { it.isUrlSupported(url) }
    if (services.size > 1) {
      Notifications.Bus.notify(Notification("Custom plugin repository auth",
                                            "",
                                            "There are too many authorization services supporting $url: ${services.map { it.name }.joinToString(",")}",
                                            NotificationType.WARNING))
      return null
    }
    return if (services.size == 1) {
      val service = services.first()
      val headers = service.generateAuthHeaders(url)
      if (headers == null) {
        Notifications.Bus.notify(Notification("Custom plugin repository auth",
                                              "",
                                              "To download plugins from $url you need to authorize in ${service.name}",
                                              NotificationType.WARNING))
      }
      headers
    }
    else null
  }

  fun addAuthHeadersIfTheyExist(tuner: URLConnection, url: String){
    getUrlAuthHeaders(url)?.forEach { (K, V) ->tuner.addRequestProperty(K, V) }
  }
}