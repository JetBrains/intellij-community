// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Executes given action for each client connected to all projects opened in IDE
 *
 * **Note:** This method should not be called within a suspend context.
 * It is recommended to use [Application.forEachSessionSuspending] instead.
 */
@RequiresBlockingContext
inline fun Application.forEachSession(kind: ClientKind, action: (ClientAppSession) -> Unit) {
  for (session in this.service<ClientSessionsManager<*>>().getSessions(kind)) {
    ClientId.withClientId(session.clientId) {
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
@RequiresBlockingContext
inline fun Project.forEachSession(kind: ClientKind, action: (ClientProjectSession) -> Unit) {
  for (session in this.service<ClientSessionsManager<*>>().getSessions(kind) as List<ClientProjectSession>) {
    ClientId.withClientId(session.clientId) {
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

@get:Internal
val Application.currentSession: ClientAppSession
  get() = ClientSessionsManager.getAppSession() ?: error("Application-level session is not set. ${ClientId.current}")

@get:Internal
val Project.currentSession: ClientProjectSession
  get() = ClientSessionsManager.getProjectSession(this) ?: error("Project-level session is not set. ${ClientId.current}")

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
  return ClientSessionsManager.getAppSession(clientId) ?: error("Application-level session is not found. $clientId")
}

@Internal
fun Project.session(clientId: ClientId): ClientProjectSession {
  return ClientSessionsManager.getProjectSession(this, clientId) ?: error("Project-level session is not found. $clientId")
}
