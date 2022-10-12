// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NavBarIdeUtil")

package com.intellij.ide.navbar.ide

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry

internal val isNavbarV2Enabled: Boolean = Registry.`is`("ide.navBar.v2", false)

internal val LOG: Logger = Logger.getInstance("#com.intellij.ide.navbar.ide")
