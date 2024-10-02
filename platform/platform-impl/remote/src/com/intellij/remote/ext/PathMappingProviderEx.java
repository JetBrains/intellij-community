// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ext;

import com.intellij.remote.PathMappingProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Extends PathMappingsProvider with presentation info.
 * It will be used in UI to show path mappings in groups with provider description.
 */
public abstract class PathMappingProviderEx extends PathMappingProvider {

  public abstract @NotNull PathMappingType getMappingType();
}
