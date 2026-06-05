// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// An alternative to [com.intellij.openapi.vfs.newvfs.BulkFileListener] that allows
/// for moving parts of VFS event processing to background thread and thus reduce the duration of UI freezes.
///
/// ### Threading contract:
///
/// [prepareChange] is always called in a read-action on a background thread.
///
/// The thread of execution of [ChangeApplier] is determined by the method of registration.
///
///   - (Recommended) Appliers runs on **background thread** if it was registered with `com.intellij.vfs.asyncListenerBackgroundable`
///     or [VirtualFileManager#addAsyncFileListenerBackgroundable]
///   - Appliers runs on the **EDT** if it was registered with `com.intellij.vfs.asyncListener`
///     or [VirtualFileManager#addAsyncFileListener]
///
/// In the future versions of IntelliJ Platform, this listener may start running on background threads unconditionally.
///
/// ### Migration of synchronous listeners:
///
/// Synchronous listeners have two flavors: "before" and "after"; observing the state of the system before and after a VFS change, respectively.
/// Since asynchronous listeners are executed before applying VFS events, they're more easily suited to "before" event processing. Note that
/// not all synchronous listeners need to be migrated, only those that might take noticeable time.
///
/// To migrate a "before"-handler, you need to split the listener into two parts: one that analyzes the events but has no side effects,
/// and one which actually modifies some other subsystem's state based on these events. The first part then goes into [#prepareChange],
/// and the second one into [ChangeApplier#beforeVfsChange()]. Please ensure that the ordering of events
/// (if it's important) isn't lost during this splitting, and that your listener can handle several events about the same file.
/// If your listener depends on a state not synchronized using read-write actions, be aware that this state can be changed
/// during [#prepareChange] or before [ChangeApplier#beforeVfsChange()].
/// Take care to synchronize the state properly and handle its changes.
///
/// The "after"-part is more complicated, as it might need to observe the state of the whole system (e.g., VFS, project model, PSI) after
/// the file system is changed. So moving these computations into "before"-part might not be straightforward. It's still possible sometimes:
/// e.g., if you only check for changed file names, or whether some file (non-directory) with a specific name is created under the project's roots.
/// In this case the check can go into [#prepareChange], and the action based on it — into [ChangeApplier#afterVfsChange()].
///
/// When you migrate a listener with both "before" and "after" parts, you can try to just move the whole "after"-processing into
/// [ChangeApplier#afterVfsChange()]. But make it as fast as possible to shorten the UI freezes.
///
/// If possible, consider moving heavy processing into background threads and/or performing it lazily.
/// There's no general solution, each "after" event processing should be evaluated separately considering the needs and contracts
/// of each subsystem it serves. Note that it'll likely need a consistent model of the world, probably with the help of
/// [com.intellij.openapi.application.ReadAction#nonBlocking] (and note that other changes might happen after yours, and
/// the state of the system can change when your asynchronous handler starts). This might also
/// introduce a discrepancy in the world when the VFS is already changed, but other subsystems aren't, and this discrepancy
/// should be made as explicit as possible.
public interface AsyncFileListener {
  /// Called (possibly on a background thread) when a batch of VFS events is ready to be fired.
  /// This method should not have side effects and should guarantee its cancellability by calling
  /// [ProgressManager#checkCanceled()] often enough.
  ///
  /// Note that this listener can only observe the state of the system before VFS events, and so
  /// it can't work with anything that would be after them. E.g., there will be no file in
  /// [VFileCreateEvent], [com.intellij.openapi.roots.FileIndexFacade]
  /// won't know anything about the updated state after VFS change, etc.
  ///
  /// Note that the events posted passed might differ from the ones passed into [com.intellij.openapi.vfs.newvfs.BulkFileListener].
  /// In particular, there may occasionally be several events about the same file (e.g., deletion and then creation, or creation and
  /// then deletion of the file's parent). Hence, the order of events is significant. Implementations should be prepared for such situations.
  ///
  /// @return a ChangeApplier object to be called inside a write action before/after the passed events are finally applied to VFS
  @Contract(pure = true)
  @Nullable ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events);

  interface ChangeApplier {
    /// This method is called in a write action before the VFS events are delivered and applied and allows
    /// to apply modifications based on the information calculated during [#prepareChange].
    ///
    /// Although it's guaranteed that no write-actions happen between [#prepareChange] and invoking all [ChangeApplier]s,
    /// another listener might already have changed something (e.g., send PSI events, increase modification trackers, etc.)
    /// by the time this implementation is executed, so be prepared. And if your listener depends on state not synchronized via read-write actions,
    /// it can be changed by this moment as well.
    @RequiresWriteLock
    // the thread of execution depends on method of registration
    default void beforeVfsChange() {}

    /// This method is called in a write action after the VFS events are delivered and applied and allows
    /// to apply modifications based on the information calculated during [#prepareChange].
    /// The implementations should be as fast as possible.
    ///
    /// If you process events passed into [#prepareChange] here, remember that an event might be superseded by further events
    /// from the same list. For example, the [VFileEvent#getFile()] may be invalid (if it was deleted by that further event),
    /// [VFileCreateEvent#getFile()] may return null, property value in
    /// [com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent] may be already outdated, etc.
    ///
    /// Note that if a directory with other files/directories inside is created,
    /// this listener will receive [VFileCreateEvent] only for the topmost directory.
    /// To iterate over all the created files, use [com.intellij.openapi.roots.FileIndex#iterateContentUnderDirectory]
    /// and provide the file from the [VFileCreateEvent].
    /// Otherwise, excluded files might be added to the VFS, which may lead to performance problems.
    @RequiresWriteLock
    // the thread of execution depends on method of registration
    default void afterVfsChange() {}
  }
}
