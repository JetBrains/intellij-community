// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * An internal interface to perform index search optimization based on scope.
 * @see VirtualFileEnumeration
 */
@ApiStatus.Internal
public interface VirtualFileEnumerationAware {
  @Nullable VirtualFileEnumeration extractFileEnumeration();
}
