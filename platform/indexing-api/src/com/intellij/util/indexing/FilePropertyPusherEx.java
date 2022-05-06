// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface FilePropertyPusherEx<T> extends FilePropertyPusher<T> {

  boolean acceptsOrigin(@NotNull Project project, @NotNull IndexableSetOrigin origin);

  @Nullable
  T getImmediateValueEx(@NotNull IndexableSetOrigin origin);
}
