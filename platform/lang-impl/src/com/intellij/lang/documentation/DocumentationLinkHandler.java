// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Experimental
public interface DocumentationLinkHandler {

  @Internal
  ExtensionPointName<DocumentationLinkHandler> EP_NAME = ExtensionPointName.create("com.intellij.lang.documentationLinkHandler");

  /**
   * @return result of resolving the given {@code url} to another {@link DocumentationTarget},
   * or {@code null} if this handler is not applicable, or the target cannot be resolved
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  default @Nullable LinkResolveResult resolveLink(@NotNull DocumentationTarget target, @NotNull String url) {
    return null;
  }

  /**
   * Implement this method to load some additional data when a link is activated,
   * and then to update the documentation browser content with the loaded data.
   *
   * @return a stream of updates, which should be applied to the existing content,
   * or {@code null} if this handler is not applicable
   */
  @Internal
  @RequiresReadLock
  @RequiresBackgroundThread
  default @Nullable ContentUpdater contentUpdater(@NotNull DocumentationTarget target, @NotNull String url) {
    return null;
  }
}
