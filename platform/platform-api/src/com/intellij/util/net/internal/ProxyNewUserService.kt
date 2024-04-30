// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.internal

import org.jetbrains.annotations.ApiStatus

/**
 * This is a **temporary** hack for switching the default in HttpConfigurable. Do not use.
 * It will be removed once HttpConfigurable is deprecated and migration to a new API for proxy settings is made.
 */
@ApiStatus.Internal
interface ProxyNewUserService {
  fun isNewUser(): Boolean
}