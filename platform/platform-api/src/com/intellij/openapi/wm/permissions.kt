// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.platform.ide.core.permissions.Permission
import com.intellij.platform.ide.core.permissions.impl.IdePermission
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
val toolWindowViewAccess: Permission = IdePermission("ide.access.toolwindow")

@ApiStatus.Experimental
val fullToolWindowAccess: Permission = IdePermission("ide.access.toolwindow")