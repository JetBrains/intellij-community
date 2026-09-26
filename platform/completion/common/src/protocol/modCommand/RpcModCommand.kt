// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol.modCommand

import kotlinx.serialization.Serializable

/**
 * Serializable representation of [com.intellij.modcommand.ModCommand] for RPC transfer.
 *
 * Only a subset of ModCommand types commonly used in completion are supported.
 * Unsupported types will cause fallback to backend insertion.
 */
@Serializable
sealed interface RpcModCommand
