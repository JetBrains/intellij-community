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
 * of UI freezes. Asynchronous listeners should preferably be registered as "vfs.asyncListener" extensions.
 * If that's too inconvenient, manual registration via {@link VirtualFileManager#addAsyncFileListener} is possible.<p></p>
 *
 * <h3>Migration of synchronous listeners:</h3>
 *
 * Synchronous listeners have two flavours: "before" and "after"; observing the state of the system before and after a VFS change, respectively.
 * Since asynchronous listeners are executed before applying VFS events, they're more easily suited to "before" event processing. Note that
 * not all synchronous listeners need to be migrated, only those that might take noticeable time.<p></p>
 *
 * To migrate a "before"-handler, you need to split the listener into two parts: one that analyzes the events but has no side effects,
 * and one which actually modifies some other subsystem's state based on these events. The first part then goes into {@link #prepareChange},
 * and the second one into {@link ChangeApplier#beforeVfsChange()}. Please ensure that the ordering of events
 * (if it's important) isn't lost during this splitting, and that your listener can handle several events about the same file.<p></p>
 *
 * The "after"-part is more complicated, as it might need to observe the state of the whole system (e.g. VFS, project model, PSI) after
 * the file system is changed. So moving these computations into "before"-part might not be straightforward. It's still possible sometimes:
 * e.g. if you only check for changed file names, or whether some file (non-directory) with a specific name is created under project's roots.
 * In this case the check can go into {@link #prepareChange}, and the action based on it &mdash; into {@link ChangeApplier#afterVfsChange()}.
 * <p></p>
 *
 * When you migrate a listener with both "before" and "after" parts, you can try to just move the whole "after"-processing into
 * {@link ChangeApplier#afterVfsChange()}. But make it as fast as possible to shorten the UI freezes.<p></p>
 *
 * If possible, consider moving heavy processing into background threads and/or performing it lazily.
 * There's no general solution, each "after" event processing should be evaluated separately considering the needs and contracts
 * of each specific subsystem it serves. Note that it'll likely need a consistent model of the world, probably with the help of
 * {@link com.intellij.openapi.application.ReadAction#nonBlocking} (and note that other changes might happen after yours, and
 * the state of the system can change when your asynchronous handler starts). This might also
 * introduce a discrepancy in the world when the VFS is already changed but other subsystems aren't, and this discrepancy
 * should be made as explicit as possible.
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
    /**
     * This method is called in write action before the VFS events are delivered and applied, and allows
     * to apply modifications based on the information calculated during {@link #prepareChange}.
     * The implementations should be as fast as possible.
     */
    default void beforeVfsChange() {}

    /**
     * This method is called in write action after the VFS events are delivered and applied, and allows
     * to apply modifications based on the information calculated during {@link #prepareChange}.
     * The implementations should be as fast as possible.
     */
    default void afterVfsChange() {}
  }
}
