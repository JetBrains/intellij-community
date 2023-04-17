// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * Use [NavigationRequests] to create instances.
 *
 * @see [NavigationRequests.sourceNavigationRequest]
 * @see [NavigationRequests.rawNavigationRequest]
 */
@Experimental
@NonExtendable // sealed
interface NavigationRequest
