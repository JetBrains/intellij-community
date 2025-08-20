// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A listener for VFS events, invoked inside write-action. Depending on the way of registration, can be invoked on any thread
 * <p>
 * Please use {@link com.intellij.openapi.vfs.AsyncFileListener} instead, unless you absolutely sure you need to receive events synchronously.
 * <p>
 * To register this listener, use e.g. {@code project.getMessageBus().connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES_BG, listener)} (preferrable),
 * or {@code project.getMessageBus().connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, listener)}
 * or define the listener in {@code plugin.xml} as an application listener (the preferred way):
 * <pre>
 * &lt;applicationListeners>
 *   &lt;listener class="com.plugin.MyBulkFileListener"
 *             topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"/>
 * &lt;/applicationListeners>
 * </pre>
 * </p>
 *
 * <p>Please note that the VFS events are project-agnostic so all listeners will be notified about events from all open projects.
 * For filtering the events use {@link com.intellij.openapi.roots.ProjectRootManager#getFileIndex} with
 * {@link com.intellij.openapi.roots.FileIndex#isInContent}.</p>
 */
public interface BulkFileListener {
  @RequiresWriteLock
  // currently executed on EDT
  default void before(@NotNull List<? extends @NotNull VFileEvent> events) { }

  @RequiresWriteLock
  // currently executed on EDT
  default void after(@NotNull List<? extends @NotNull VFileEvent> events) { }
}
