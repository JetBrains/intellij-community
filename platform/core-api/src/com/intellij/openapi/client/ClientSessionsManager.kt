// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<ClientSessionsManager<*>>()
@ApiStatus.Experimental
@ApiStatus.Internal
open class ClientSessionsManager<T : ClientSession> {
  companion object {
    /**
     * Returns an application-level session for a particular client.
     * @see ClientSession
     */
    @JvmStatic
    @JvmOverloads
    fun getAppSession(clientId: ClientId = ClientId.current): ClientAppSession? {
      return ApplicationManager.getApplication()?.service<ClientSessionsManager<ClientAppSession>>()?.getSession(clientId)
    }

    /**
     * Returns all application-level sessions.
     * @param kind specifies what sessions should be included
     * @see ClientSession
     */
    @JvmStatic
    fun getAppSessions(kind: ClientKind): List<ClientAppSession> {
      return ApplicationManager.getApplication()?.service<ClientSessionsManager<ClientAppSession>>()?.getSessions(kind) ?: emptyList()
    }

    /**
     * Returns a project-level session for a particular client.
     * @see ClientProjectSession
     */
    @JvmStatic
    @JvmOverloads
    fun getProjectSession(project: Project, clientId: ClientId = ClientId.current): ClientProjectSession? {
      return project.service<ClientSessionsManager<ClientProjectSession>>().getSession(clientId)
    }

    /**
     * Returns all project-level sessions.
     * @param kind specifies what sessions should be included
     * @see ClientSession
     */
    @JvmStatic
    fun getProjectSessions(project: Project, kind: ClientKind): List<ClientProjectSession> {
      return project.service<ClientSessionsManager<ClientProjectSession>>().getSessions(kind)
    }
  }

  private val sessions = ConcurrentHashMap<ClientId, T>()

  fun getSessions(kind: ClientKind): List<T> {
    if (kind == ClientKind.ALL) {
      return java.util.ArrayList(sessions.values)
    }
    else {
      return sessions.values.filter { it.type.matches(kind) }
    }
  }

  fun getSession(clientId: ClientId): T? {
    return sessions[clientId]
  }

  fun registerSession(disposable: Disposable, session: T) {
    val clientId = session.clientId
    if (sessions.putIfAbsent(clientId, session) != null) {
      LOG.error("Session $session with such clientId is already registered")
    }
    LOG.debug("Session added '$session'")

    Disposer.register(disposable, session)
    Disposer.register(disposable) {
      sessions.remove(clientId)
      LOG.debug("Session removed '$clientId'")
    }
  }

  @ApiStatus.Obsolete
  fun isValid(clientId: ClientId): Boolean {
    return getSession(clientId)?.isDisposed == false
  }
}