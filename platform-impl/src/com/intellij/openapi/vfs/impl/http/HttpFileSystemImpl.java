package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.openapi.vfs.ex.http.HttpVirtualFileListener;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class HttpFileSystemImpl extends HttpFileSystem {
  private RemoteFileManager myRemoteFileManager;
  private EventDispatcher<HttpVirtualFileListener> myDispatcher = EventDispatcher.create(HttpVirtualFileListener.class);
  private List<RemoteContentProvider> myProviders = new ArrayList<RemoteContentProvider>();
  private final DefaultRemoteContentProvider myDefaultRemoteContentProvider;

  public HttpFileSystemImpl() {
    myRemoteFileManager = new RemoteFileManager(this);
    myDefaultRemoteContentProvider = new DefaultRemoteContentProvider();
  }

  public VirtualFile findFileByPath(@NotNull String path) {
    return findFileByPath(path, false);
  }

  public VirtualFile findFileByPath(@NotNull String path, boolean isDirectory) {
    try {
      String url = VirtualFileManager.constructUrl(PROTOCOL, path);
      RemoteContentProvider provider = findContentProvider(url);
      return myRemoteFileManager.getOrCreateFile(url, path, isDirectory, provider);
    }
    catch (IOException e) {
      return null;
    }
  }

  @NotNull
  private RemoteContentProvider findContentProvider(final @NotNull String url) {
    for (RemoteContentProvider provider : myProviders) {
      if (provider.canProvideContent(url)) {
        return provider;
      }
    }
    return myDefaultRemoteContentProvider;
  }

  public boolean isFileDownloaded(@NotNull final VirtualFile file) {
    return file instanceof HttpVirtualFile && ((HttpVirtualFile)file).getFileInfo().isDownloaded();
  }

  public void addRemoteContentProvider(@NotNull final RemoteContentProvider provider, @NotNull Disposable parentDisposable) {
    addRemoteContentProvider(provider);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeRemoteContentProvider(provider);
      }
    });
  }

  public void addRemoteContentProvider(@NotNull RemoteContentProvider provider) {
    myProviders.add(provider);
  }

  public void removeRemoteContentProvider(@NotNull RemoteContentProvider provider) {
    myProviders.remove(provider);
  }

  public void addFileListener(@NotNull final HttpVirtualFileListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void addFileListener(@NotNull final HttpVirtualFileListener listener, @NotNull final Disposable parentDisposable) {
    addFileListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeFileListener(listener);
      }
    });
  }

  public void removeFileListener(@NotNull final HttpVirtualFileListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void fireFileDownloaded(@NotNull VirtualFile file) {
    myDispatcher.getMulticaster().fileDownloaded(file);
  }

  public void disposeComponent() {
    myRemoteFileManager.getStorage().deleteDownloadedFiles();
  }

  public void initComponent() { }

  public VirtualFile createChildDirectory(Object requestor, VirtualFile vDir, String dirName) throws IOException {
    throw new UnsupportedOperationException();
  }

  public VirtualFile createChildFile(Object requestor, VirtualFile vDir, String fileName) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void deleteFile(Object requestor, VirtualFile vFile) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void moveFile(Object requestor, VirtualFile vFile, VirtualFile newParent) throws IOException {
    throw new UnsupportedOperationException();
  }

  public VirtualFile copyFile(Object requestor, VirtualFile vFile, VirtualFile newParent, final String copyName) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void renameFile(Object requestor, VirtualFile vFile, String newName) throws IOException {
    throw new UnsupportedOperationException();
  }

  public String getProtocol() {
    return PROTOCOL;
  }

  public String extractPresentableUrl(String path) {
    return VirtualFileManager.constructUrl(PROTOCOL, path);
  }

  public VirtualFile refreshAndFindFileByPath(String path) {
    return findFileByPath(path);
  }

  public void refresh(boolean asynchronous) {
  }

  @NotNull
  public String getComponentName() {
    return "HttpFileSystem";
  }
}
