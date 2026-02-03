// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ClientSessionsUtil")
@file:Suppress("UNCHECKED_CAST", "unused", "UnusedReceiverParameter")

package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Executes given action for each client connected to all projects opened in IDE
 *
 * **Note:** This method should not be called within a suspend context.
 * It is recommended to use [Application.forEachSessionSuspending] instead.
 */
fun Application.forEachSession(kind: ClientKind, action: (ClientAppSession) -> Unit) {
  for (session in this.service<ClientSessionsManager<*>>().getSessions(kind)) {
    ClientId.withExplicitClientId(session.clientId) {
      logger<ClientSessionsManager<*>>().runAndLogException {
        action(session as ClientAppSession)
      }
    }
  }
}

/**
 * Executes given action for each client connected to all projects opened in IDE
 */
suspend fun Application.forEachSessionSuspending(kind: ClientKind, action: suspend (ClientAppSession) -> Unit) {
  for (session in this.service<ClientSessionsManager<*>>().getSessions(kind)) {
    withContext(session.clientId.asContextElement()) {
      logger<ClientSessionsManager<*>>().runAndLogException {
        action(session as ClientAppSession)
      }
    }
  }
}

/**
 * Executes given action for each client connected to this [Project]
 *
 * **Note:** This method should not be called within a suspend context.
 * It is recommended to use [Project.forEachSessionSuspending] instead.
 */
fun Project.forEachSession(kind: ClientKind, action: (ClientProjectSession) -> Unit) {
  for (session in this.service<ClientSessionsManager<*>>().getSessions(kind) as List<ClientProjectSession>) {
    ClientId.withExplicitClientId(session.clientId) {
      logger<ClientSessionsManager<*>>().runAndLogException {
        action(session)
      }
    }
  }
}

/**
 * Executes given action for each client connected to this [Project]
 */
suspend fun Project.forEachSessionSuspending(kind: ClientKind, action: suspend (ClientProjectSession) -> Unit) {
  for (session in this.service<ClientSessionsManager<*>>().getSessions(kind) as List<ClientProjectSession>) {
    withContext(session.clientId.asContextElement()) {
      logger<ClientSessionsManager<*>>().runAndLogException {
        action(session)
      }
    }
  }
}

/**
 * Shortcut to [com.intellij.openapi.client.ClientSessionsManager.getAppSessionOrThrow] for [ClientId.current]
 * @see com.intellij.openapi.client.ClientSessionsManager.getAppSessionOrThrow
 */
@get:Internal
val Application.currentSession: ClientAppSession
  get() = ClientSessionsManager.getAppSessionOrThrow(application = this, clientId = ClientId.current)

/**
 * Shortcut to [com.intellij.openapi.client.ClientSessionsManager.getAppSession] for [ClientId.current]
 * @see com.intellij.openapi.client.ClientSessionsManager.getAppSession
 */
@get:Internal
val Application.currentSessionOrNull: ClientAppSession?
  get() = ClientSessionsManager.getAppSession(application = this, clientId = ClientId.current)

/**
 * Shortcut to [com.intellij.openapi.client.ClientSessionsManager.getProjectSessionOrThrow] for [ClientId.current]
 * @see com.intellij.openapi.client.ClientSessionsManager.getProjectSessionOrThrow
 */
@get:Internal
val Project.currentSession: ClientProjectSession
  get() = ClientSessionsManager.getProjectSessionOrThrow(project = this, clientId = ClientId.current)

/**
 * Shortcut to [com.intellij.openapi.client.ClientSessionsManager.getProjectSession] for [ClientId.current]
 * @see com.intellij.openapi.client.ClientSessionsManager.getProjectSession
 */
@get:Internal
val Project.currentSessionOrNull: ClientProjectSession?
  get() = ClientSessionsManager.getProjectSession(project = this, clientId = ClientId.current)

@Internal
fun Application.sessions(kind: ClientKind): List<ClientAppSession> {
  return ClientSessionsManager.getAppSessions(kind)
}

@Internal
fun Project.sessions(kind: ClientKind): List<ClientProjectSession> {
  return ClientSessionsManager.getProjectSessions(this, kind)
}

@Internal
fun Application.session(clientId: ClientId): ClientAppSession {
  return ClientSessionsManager.getAppSessionOrThrow(clientId)
}

@Internal
fun Project.session(clientId: ClientId): ClientProjectSession {
  return ClientSessionsManager.getProjectSessionOrThrow(this, clientId)
}
