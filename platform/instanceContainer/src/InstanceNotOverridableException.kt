// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer

import org.jetbrains.annotations.ApiStatus.Internal

class InstanceNotOverridableException @Internal internal constructor(
  serviceInterface: String,
  existingImpl: String,
  newImpl: String?,
) : IllegalStateException("Override failed for $serviceInterface: " +
                          if (newImpl != null)
                            "$newImpl attempts to override $existingImpl, but the existing implementation is not overriddable (not declared as 'open=true')"
                          else
                            "Existing instance $existingImpl is attempted to be removed, but it is not overriddable (not declared as 'open=true')")
