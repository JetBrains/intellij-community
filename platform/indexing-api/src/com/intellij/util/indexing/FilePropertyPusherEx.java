// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * As of 25.2 {@link FilePropertyPusher} allows pushing properties outside module content only
 * through {@link FilePropertyPusher#initExtra(Project)} and {@link FilePropertyPusher#afterRootsChanged(Project)}.
 * Such API enforced pushing values to all related files in project, ignoring information about the scope of the current scanning.
 * It's inefficient, and may result in a performance issue (see IJPL-2963).
 * <p>
 * {@link FilePropertyPusherEx} allows selecting applicable {@link IndexableSetOrigin}s of current scanning, and push new values only there,
 * making pusher's work incremental instead of a push to all related files
 * on every {@link com.intellij.openapi.roots.ModuleRootEvent} (which includes every scanning) and on opening a project.
 */
@Internal
public interface FilePropertyPusherEx<T> extends FilePropertyPusher<T> {

  boolean acceptsOrigin(@NotNull Project project, @NotNull IndexableSetOrigin origin);

  @Nullable
  T getImmediateValueEx(@NotNull IndexableSetOrigin origin);
}
