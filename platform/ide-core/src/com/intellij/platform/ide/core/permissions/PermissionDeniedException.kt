// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.core.permissions

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class PermissionDeniedException(val permissions: List<Permission>) : IllegalStateException("Permission denied: $permissions")