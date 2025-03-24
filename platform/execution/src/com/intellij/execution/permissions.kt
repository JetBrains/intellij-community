// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.platform.ide.core.permissions.Permission
import com.intellij.platform.ide.core.permissions.impl.IdePermission
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
val runViewAccess: Permission = IdePermission("ide.access.run")

@ApiStatus.Experimental
val fullRunAccess: Permission = IdePermission("ide.access.run.full")