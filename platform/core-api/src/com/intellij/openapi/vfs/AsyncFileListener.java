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
 */
public interface AsyncFileListener {

  /**
   * Return whether {@link #prepareChange} should be called inside a (cancellable) read action.
   * This makes sense if the listener wants consistent access to the project model, VFS or PSI.
   * Avoiding read action makes sense if the listener performs long non-cancellable operations
   * like disk access. The listener should still call {@code checkCanceled} frequently enough, and
   * it can still take read actions manually.
   */
  default boolean needsReadAction() {
    return true;
  }

  /**
   * Called (possibly on a background thread) when a batch of VFS events is ready to be fired.
   * This method should not have side effects and should guarantee its cancellability by calling
   * {@link ProgressManager#checkCanceled()} often enough.<p></p>
   *
   * Note that this listener can only observe the state of the system before VFS events, and so
   * it can't work with anything that would be after them, e.g. there will be no file in
   * {@link com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent}, {@link com.intellij.openapi.roots.FileIndexFacade}
   * won't know anything about the updated state after VFS change, and so on.<p></p>
   *
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
