// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer

import org.jetbrains.annotations.ApiStatus.Internal

class InstanceNotOverridableException @Internal internal constructor(
  message: String,
) : IllegalStateException(message)
