// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An element of the container system with which extensions may be associated. It's either {@link com.intellij.openapi.application.Application Application},
 * {@link com.intellij.openapi.project.Project Project} or {@link com.intellij.openapi.module.Module Module}.
 */
public interface AreaInstance {
  @NotNull
  @ApiStatus.Internal
  ExtensionsArea getExtensionArea();
}
