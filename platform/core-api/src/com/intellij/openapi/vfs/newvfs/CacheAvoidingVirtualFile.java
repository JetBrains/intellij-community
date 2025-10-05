// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for a virtual file implementation that avoids storing (caching) <b>new</b> entries in the VFS.
 * The {@link VirtualFile} itself could be cached or not, but e.g. {@link VirtualFile#getChildren()} method doesn't store
 * new entries in VFS cache.
 * <p/>
 * The {@link VirtualFile} implementation(s) marked by this interface may violate specific parts of the {@link VirtualFile}'s
 * contract, specifically about equality -- so should be used with caution. Consult specific implementation's documentation
 * for details.
 */
@ApiStatus.Internal
public interface CacheAvoidingVirtualFile {
  /**
   * @return a regular ('cached') file corresponding to this cache-avoiding virtual file -- or null, if such a file doesn't exist.
   * If the file wasn't cached before, making it cacheable requires caching of all file's parents, up until the nearest cached root
   * -- so this method may be a demanding operation, if the file is deep down the hierarchy, far from the nearest cached parent/root.
   */
  @Nullable VirtualFile asCacheable();

  /** @return true if this VirtualFile is cached in VFS cache, or false, if it is a completely transient instance */
  boolean isCached();
}
