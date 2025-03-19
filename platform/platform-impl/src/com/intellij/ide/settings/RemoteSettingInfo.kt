// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.settings

/**
 * Information about how some setting should be synchronized between backend and frontend.
 *
 * **Important**: [allowedInCwm] can be `true` only if the settings component
 * is registered as a per-client service and marked with `@State(perClient = true)`
 * (see [EditorSettingsExternalizable][com.intellij.openapi.editor.ex.EditorSettingsExternalizable]
 * as an example, also see [ServiceDescriptor#client][com.intellij.openapi.components.ServiceDescriptor.client])
 */
class RemoteSettingInfo(
  val direction: Direction,
  /**
   * `true`, if this setting should be synchronized for a CodeWithMe guest.
   * Only if the settings component is a per-client service, see documentation for [RemoteSettingInfo].
   */
  val allowedInCwm: Boolean = false
) {
  enum class Direction(
    /**
     * Sending endpoint (for all events or only for initial events)
     */
    val from: Endpoint,
    /**
     * Receiving endpoint (for all events or only for initial events)
     */
    val to: Endpoint,
    /**
     * * `true` if all events are sent only from [from] to [to]
     * * `false` if events are sent both ways, except from initial changes; they are sent only from [from] to [to]
     */
    val isOneDirectionOnly: Boolean
  ) {
    /**
     * Sync only changes from the backend to the frontend
     */
    OnlyFromBackend(from = Endpoint.Backend, to = Endpoint.Frontend, isOneDirectionOnly = true),

    /**
     * Sync only changes from the frontend to the backend
     */
    OnlyFromFrontend(from = Endpoint.Frontend, to = Endpoint.Backend, isOneDirectionOnly = true),

    /**
     * Sync both ways, but take the initial value from the backend
     */
    InitialFromBackend(from = Endpoint.Backend, to = Endpoint.Frontend, isOneDirectionOnly = false),

    /**
     * Sync both ways, but take the initial value from the frontend
     */
    InitialFromFrontend(from = Endpoint.Frontend, to = Endpoint.Backend, isOneDirectionOnly = false),

    /**
     * Do not synchronize at all
     */
    DoNotSynchronize(from = Endpoint.None, to = Endpoint.None, isOneDirectionOnly = /* doesn't mean anything here */ true)
  }

  enum class Endpoint {
    Backend,
    Frontend,
    None
  }
}