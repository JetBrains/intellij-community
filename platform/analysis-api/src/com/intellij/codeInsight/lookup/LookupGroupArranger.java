// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.ApiStatus;

/**
 * The LookupGroupArranger interface is designed to manage the behavior of grouped elements in lookup components.
 * It provides a mechanism to enable or disable support for groups in external contexts.
 */
@ApiStatus.Internal
public interface LookupGroupArranger {
  void synchronizeGroupSupport(boolean supportGroupsExternally);
}
