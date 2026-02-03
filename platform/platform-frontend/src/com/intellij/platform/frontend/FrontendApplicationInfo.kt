// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.frontend

// TODO: uncomment it when IJPL-163613 is fixed
//   now it is moved to intellij.platform.ide.impl module see com.intellij.frontend.FrontendApplicationInfo
//@ApiStatus.Internal
//object FrontendApplicationInfo {
//  val frontendTypeDataKey = Key<FrontendType>("platform.frontend.type")
//
//  /**
//   * Determines the type of the frontend.
//   *
//   * Use this function as rare as possible and only for temporary solutions.
//   * Ideally, code should not work conditionally based on the [FrontendType].
//   */
//  fun getFrontendType(): FrontendType {
//    // [frontendTypeDataKey] is put by thin client (RemoteDev and Code With Me), otherwise it is Monolith
//    return application.getUserData(frontendTypeDataKey) ?: FrontendType.Monolith
//  }
//}
//
///**
// * Enum representing different types of IntelliJ Platform Frontends.
// * Namely:
// * - Monolith: Represents a single process IDE.
// * - RemoteDev: Represents a frontend in a remote development and Code With Me client.
// */
//@ApiStatus.Internal
//sealed interface FrontendType {
//  object Monolith : FrontendType
//
//  /**
//   * Represents a frontend in a remote development and Code With Me client.
//   *
//   * @property isLuxSupported Indicates whether the frontend supports LUX (transferring UI from host to the client).
//   */
//  data class RemoteDev(val isLuxSupported: Boolean) : FrontendType
//}