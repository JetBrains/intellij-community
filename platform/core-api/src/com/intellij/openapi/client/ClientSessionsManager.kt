// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

private val LOG = logger<ClientSessionsManager<*>>()

@ApiStatus.Experimental
@ApiStatus.Internal
open class ClientSessionsManager<T : ClientSession>(private val scope: CoroutineScope) {
  companion object {
    /**
     * Returns an application-level session for a particular [clientId].
     * @see ClientSession
     * @see com.intellij.openapi.application.Application.currentSessionOrNull
     */
    @JvmStatic
    fun getAppSession(clientId: ClientId): ClientAppSession? {
      return getAppSession(ApplicationManager.getApplication(), clientId)
    }

    /**
     * Returns an application-level session for a particular [clientId] and given [application].
     * @see ClientSession
     * @see com.intellij.openapi.application.Application.currentSessionOrNull
     */
    @JvmStatic
    fun getAppSession(application: Application?, clientId: ClientId): ClientAppSession? {
      return application?.service<ClientSessionsManager<ClientAppSession>>()?.getSession(clientId)
    }

    /**
     * Returns an application-level session for a particular [clientId]
     * @throws [ProcessCanceledException] if the session was disposed recently or [IllegalStateException] if there is no session for [clientId]
     * @see ClientSession
     * @see com.intellij.openapi.application.Application.currentSession
     */
    @JvmStatic
    fun getAppSessionOrThrow(clientId: ClientId): ClientAppSession {
      return getAppSessionOrThrow(ApplicationManager.getApplication() ?: error("ApplicationManager.getApplication() is null"), clientId)
    }

    /**
     * Returns an application-level session for a particular [clientId] and [application]
     * @throws [ProcessCanceledException] if the session was disposed recently or [IllegalStateException] if there is no session for [clientId]
     * @see ClientSession
     * @see com.intellij.openapi.application.Application.currentSession
     */
    @JvmStatic
    fun getAppSessionOrThrow(application: Application, clientId: ClientId): ClientAppSession {
      val (session, disposed) = application.service<ClientSessionsManager<ClientAppSession>>().getSessionAndDisposedStatus(clientId)
      if (disposed) cancelled("Application-level session is disposed for $clientId")
      return session ?: error("Application-level session is not set for $clientId")
    }

    /**
     * Returns all application-level sessions.
     * @param kind specifies what sessions should be included
     * @see ClientSession
     * @see com.intellij.openapi.application.Application.currentSessionOrNull
     */
    @JvmStatic
    fun getAppSessions(kind: ClientKind): List<ClientAppSession> {
      return ApplicationManager.getApplication()?.service<ClientSessionsManager<ClientAppSession>>()?.getSessions(kind) ?: emptyList()
    }

    /**
     * Returns a project-level session for a particular [clientId] and [project].
     * @see ClientProjectSession
     * @see com.intellij.openapi.project.Project.currentSessionOrNull
     */
    @JvmStatic
    fun getProjectSession(project: Project, clientId: ClientId): ClientProjectSession? {
      return project.service<ClientSessionsManager<ClientProjectSession>>().getSession(clientId)
    }

    /**
     * Returns a project-level session for a particular [clientId] and [project]
     * @throws [ProcessCanceledException] if the session was disposed recently or [IllegalStateException] if there is no session for [clientId] and [project]
     * @see ClientSession
     * @see com.intellij.openapi.project.Project.currentSession
     */
    @JvmStatic
    fun getProjectSessionOrThrow(project: Project, clientId: ClientId): ClientProjectSession {
      val (session, disposed) = project.service<ClientSessionsManager<ClientProjectSession>>().getSessionAndDisposedStatus(clientId)
      if (disposed) cancelled("Project-level session is disposed for $clientId")
      return session ?: error("Project-level session is not set for $clientId")
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

    private fun cancelled(message: String): Nothing = throw ProcessCanceledException(AlreadyDisposedException(message))

    private val disposedRemovalDelay = 1.minutes
  }

  private val sessions = ConcurrentHashMap<ClientId, T>()
  private val recentlyDisposedSessions = ContainerUtil.newConcurrentSet<ClientId>()

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
    // the same session probably can be disposed before
    recentlyDisposedSessions.remove(clientId)
    if (sessions.putIfAbsent(clientId, session) != null) {
      LOG.error("Session $session with such clientId is already registered")
    }
    LOG.debug { "Session added '$session'" }

    Disposer.register(disposable, session)
    Disposer.register(disposable) {
      recentlyDisposedSessions.add(clientId)
      sessions.remove(clientId)
      LOG.debug { "Session for '$clientId' removed and added to recently disposed" }
      scope.launch {
        delay(disposedRemovalDelay)
        recentlyDisposedSessions.remove(clientId)
        LOG.debug("Session for '$clientId' removed from recently disposed after $disposedRemovalDelay")
      }
    }
  }

  @ApiStatus.Obsolete
  @Deprecated(message = "Use `!session.isDisposed` instead or better run coroutine from per-client scope that will be cancelled when a client has gone",
              level = DeprecationLevel.ERROR, replaceWith = ReplaceWith("!session.isDisposed"))
  fun isValid(clientId: ClientId): Boolean {
    return getSession(clientId)?.isDisposed == false
  }

  private fun getSessionAndDisposedStatus(clientId: ClientId): Pair<T?, Boolean> {
    val session = getSession(clientId)
    if (session == null && recentlyDisposedSessions.contains(clientId)) return null to true
    return session to (session?.isDisposed == true)
  }
}