// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;

/**
 * This marker interface may be used on {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor}
 * to avoid adding its roots as watched to VFS.
 * <p>
 * As a rule of thumb roots registered in project should be watched by VFS, otherwise it won't acknowledge IDE about changes under it.
 * But sometimes, when registered roots are under already registered ones (for example, content roots),
 * and there is significant number of them (up to 500 000), it's better to simply ignore them.
 *
 * @see WatchedRootsProvider
 * @see AdditionalLibraryRootsProvider#getRootsToWatch(Project)
 */
@ApiStatus.Experimental
public interface SkipAddingToWatchedRoots {
}
