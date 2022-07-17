// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation

import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * Use [NavigationService] to create instances.
 *
 * @see [NavigationService.sourceNavigationRequest]
 * @see [NavigationService.rawNavigationRequest]
 */
@Experimental
@NonExtendable // sealed
interface NavigationRequest
