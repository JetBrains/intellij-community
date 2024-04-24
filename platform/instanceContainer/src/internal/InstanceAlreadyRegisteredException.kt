// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

class InstanceAlreadyRegisteredException internal constructor(
  keyClassName: String,
  existingInstanceClassName: String,
  newInstanceClassName: String?,
) : IllegalStateException(
  "$keyClassName is already registered: ${existingInstanceClassName}. Failed to register: ${newInstanceClassName ?: "<removed>"}."
)
