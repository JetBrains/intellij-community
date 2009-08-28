package com.intellij.openapi.vfs.ex.http;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class HttpFileSystem extends DeprecatedVirtualFileSystem implements ApplicationComponent {
  @NonNls public static final String PROTOCOL = "http";

  public static HttpFileSystem getInstance() {
    return ApplicationManager.getApplication().getComponent(HttpFileSystem.class);
  }

  public abstract boolean isFileDownloaded(@NotNull VirtualFile file);

  public abstract void addFileListener(@NotNull HttpVirtualFileListener listener);

  public abstract void addFileListener(@NotNull HttpVirtualFileListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeFileListener(@NotNull HttpVirtualFileListener listener);

}