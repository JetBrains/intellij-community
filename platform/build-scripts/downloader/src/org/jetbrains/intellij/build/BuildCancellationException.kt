// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

/**
 * If the build script throws an exception of this type, the build will be cancelled
 * by emitting the 'buildStop' service message.
 *
 * See: https://www.jetbrains.com/help/teamcity/2022.10/service-messages.html#Canceling+Build+via+Service+Message
 */
class BuildCancellationException(cause: Throwable? = null) : RuntimeException(cause)
