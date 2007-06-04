package com.intellij.openapi.vfs.tracker;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFileListener;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public interface VirtualFileTracker {
  void addTracker(
    @NotNull String fileUrl,
    @NotNull VirtualFileListener listener,
    boolean fromRefreshOnly,
    @NotNull Disposable parentDisposable);
}
