// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.dnd

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * A drag-and-drop attached object that reports the dragged files as NIO paths.
 *
 * [FileFlavorProvider] reports a [java.io.File], so it can carry only a local file. A source that
 * drags a file from another environment, for example from a Docker container, implements this
 * interface as well. A target inside the IDE reads the paths with
 * [FileCopyPasteUtil.getPathListFromAttachedObject]. It can then copy the file with the EEL API.
 *
 * @see DroppedFileCopy
 */
@ApiStatus.Internal
interface PathFlavorProvider {
  /** Returns the dragged files, or null when the source has nothing to offer. */
  fun asPathList(): List<Path>?
}
