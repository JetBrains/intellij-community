// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Experimental
public interface DocumentationLinkResolver {

  @Internal
  ExtensionPointName<DocumentationLinkResolver> EP_NAME = ExtensionPointName.create("com.intellij.lang.documentationLinkResolver");

  /**
   * Resolves a URL in the documentation to another target.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @Nullable LinkResult resolveLink(@NotNull DocumentationTarget target, @NotNull String url);
}
