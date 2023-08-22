// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

/**
 * Defines the relation between the local network port and the target port. The
 * direction of this relation is not defined in the class and it is expected to
 * be comprehensive from the context where it is used.
 *
 * @see TargetEnvironment.localPortBindings
 */
data class ResolvedPortBinding(val localEndpoint: HostPort, val targetEndpoint: HostPort)

val ResolvedPortBinding.localPort: Int get() = localEndpoint.port

val ResolvedPortBinding.targetPort: Int get() = targetEndpoint.port