// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An alternative to {@link com.intellij.openapi.vfs.newvfs.BulkFileListener} that allows
 * for moving parts of VFS event processing to background thread and thus reduce the duration
 * of UI freezes.
 *
 * @see VirtualFileManager#addAsyncFileListener(AsyncFileListener, com.intellij.openapi.Disposable)
 */
public interface AsyncFileListener {

  /**
   * Called (possibly on a background thread) when a batch of VFS events is ready to be fired.
   * This method should not have side effects and should guarantee its cancellability by calling
   * {@link ProgressManager#checkCanceled()} often enough.<p></p>
   * <p>
   * Note that this listener can only observe the state of the system before VFS events, and so
   * it can't work with anything that would be after them, e.g. there will be no file in
   * {@link com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent}, {@link com.intellij.openapi.roots.FileIndexFacade}
   * won't know anything about the updated state after VFS change, and so on.
   * <p>
   * Note that the events posted passed might differ from the ones passed into {@link com.intellij.openapi.vfs.newvfs.BulkFileListener}.
   * In particular, there may occasionally be several events about the same file (e.g deletion and then creation, or creation and
   * then deletion of the file's parent). Hence the order of events is significant. Implementations should be prepared to such situations.
   *
   * @return a ChangeApplier object to be called inside write action before/after the passed events
   * are finally applied to VFS
   */
  @Contract(pure = true)
  @Nullable
  ChangeApplier prepareChange(@NotNull List<? extends VFileEvent> events);

  interface ChangeApplier {
    default void beforeVfsChange() {}

    default void afterVfsChange() {}
  }
}
