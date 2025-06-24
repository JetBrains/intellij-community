// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFileWithId;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * A wrapper for {@link NewVirtualFile} to avoid caching any _new_ entries in VFS during file-tree walking via this class's
 * instances. It is not bypassing the VFS cache completely, it only tries AMAP to avoid trashing the cache with new entries,
 * but if file entry already cached in VFS -- it will be used.
 * <p>
 * Most of the methods are trivially delegated to the wrapped {@link NewVirtualFile} file, except for:
 * <ol>
 *   <li>Children access: those go through {@link NewVirtualFileSystem#findCachedOrTransientFileByPath(NewVirtualFileSystem, String)}
 *   methods. I.e., all the children that are got from this file are either {@link CacheAvoidingVirtualFileWrapper},
 *   or {@link TransientVirtualFileImpl}.</li>
 *   <li>{@link com.intellij.openapi.util.UserDataHolder} trait methods: their behavior are made consistent with the behavior
 *   of apt methods of {@link TransientVirtualFileImpl}, so see it's javadoc for more details</li>
 * </ol>
 */
@ApiStatus.Internal
@VisibleForTesting
public final class CacheAvoidingVirtualFileWrapper extends VirtualFile implements VirtualFileWithId,
                                                                                  CacheAvoidingVirtualFile {
  private static final Logger LOG = Logger.getInstance(CacheAvoidingVirtualFileWrapper.class);

  private final NewVirtualFile wrappedFile;

  @VisibleForTesting
  public CacheAvoidingVirtualFileWrapper(@NotNull NewVirtualFile wrappedFile) {
    if (wrappedFile instanceof CacheAvoidingVirtualFile) {
      //nothing fundamentally wrong with double-wrapping, but there is no reason to do it intentionally, so most likely
      // it is an omission/unexpected/bug:
      throw new IllegalArgumentException(wrappedFile + " is cache-avoiding itself -- double-wrapping is not allowed");
    }
    this.wrappedFile = wrappedFile;
  }

  @Override
  public @NotNull VirtualFile asCacheable() {
    return wrappedFile;
  }

  @Override
  public boolean isCached() {
    return true;
  }

  @Override
  public VirtualFile getParent() {
    //RC: don't need findFileByPathWithoutCaching() here because if a child is cached, all it's parents must be cached too:
    //    (no way to store an fs-record without its parent fs-record)
    //    Also, by returning 'raw' virtual file we could utilise 'hierarchy-matching' -- looking up the file in a map, while
    //    going though it's parents chain.
    return wrappedFile.getParent();
  }

  @Override
  public VirtualFile[] getChildren() {
    //MAYBE RC: cache children once calculated?
    NewVirtualFileSystem fileSystem = wrappedFile.getFileSystem();
    String[] childNames = fileSystem.list(wrappedFile);
    VirtualFile[] children = new VirtualFile[childNames.length];
    for (int i = 0; i < childNames.length; i++) {
      String childName = childNames[i];
      //TODO RC: what if the child is null? it could be if e.g. the file is invalid, doesn't exist, or doesn't belong to this file system...
      children[i] = findChild(childName);
    }
    return children;
  }

  @Override
  public @Nullable VirtualFile findChild(@NotNull String childName) {
    NewVirtualFileSystem fileSystem = wrappedFile.getFileSystem();
    NewVirtualFile child = wrappedFile.findChildIfCached(childName);
    if (child != null) {
      return new CacheAvoidingVirtualFileWrapper(child);
    }
    return new TransientVirtualFileImpl(childName, getPath() + '/' + childName, fileSystem, this);
  }

  @Override
  public @Nullable VirtualFile findFileByRelativePath(@NotNull String relPath) {
    NewVirtualFileSystem fileSystem = wrappedFile.getFileSystem();
    //TODO RC: could be optimized: no need to parse and walk down the whole path from root -- we already have
    //         an intermediate node at hand, there is only few more steps down to do.
    return fileSystem.findFileByPathWithoutCaching(getPath() + '/' + relPath);
  }

  @Override
  public @NotNull VirtualFile findOrCreateChildData(Object requestor,
                                                    @NotNull String name) throws IOException {
    VirtualFile child = findChild(name);
    if (child != null) return child;
    //MAYBE RC: below we create new _cached_ child, which violates this class general contract that it does NOT create new cache
    //          entries. From one side, it seems to be explicitly requested -- but maybe it is better to still adhere the
    //          contract? Maybe it is more consistent to create a non-cached child (=fileSystem.createChildFile(requestor, parent, name))
    //          -- i.e. create physical file, and TransientVirtualFileImpl around it?
    child = createChildData(requestor, name);
    return new CacheAvoidingVirtualFileWrapper((NewVirtualFile)child);
  }

  //<editor-fold desc="VirtualFile trivial delegates"> =====================================================================================

  @Override
  public int getId() {
    return wrappedFile.getId();
  }

  @Override
  public @NotNull String getName() {
    return wrappedFile.getName();
  }

  @Override
  public @NotNull String getPath() {
    return wrappedFile.getPath();
  }

  @Override
  public @NotNull VirtualFileSystem getFileSystem() {
    return wrappedFile.getFileSystem();
  }

  @Override
  public boolean isWritable() {
    return wrappedFile.isWritable();
  }

  @Override
  public boolean isDirectory() {
    return wrappedFile.isDirectory();
  }

  @Override
  public @NotNull FileType getFileType() {
    return wrappedFile.getFileType();
  }

  @Override
  public boolean isValid() {
    return wrappedFile.isValid();
  }

  @Override
  public boolean exists() {
    return wrappedFile.exists();
  }

  @Override
  public boolean is(@NotNull VFileProperty property) {
    return wrappedFile.is(property);
  }

  @Override
  public void setWritable(boolean writable) throws IOException {
    wrappedFile.setWritable(writable);
  }

  @Override
  public @Nullable VirtualFile getCanonicalFile() {
    return wrappedFile.getCanonicalFile();
  }

  @Override
  public @Nullable String getCanonicalPath() {
    return wrappedFile.getCanonicalPath();
  }

  @Override
  public @NotNull String getNameWithoutExtension() {
    return wrappedFile.getNameWithoutExtension();
  }

  @Override
  public @Nullable String getExtension() {
    return wrappedFile.getExtension();
  }

  @Override
  public @NotNull String getPresentableUrl() {
    return wrappedFile.getPresentableUrl();
  }

  @Override
  public @NotNull String getUrl() {
    return wrappedFile.getUrl();
  }

  @Override
  public @NotNull Path toNioPath() {
    return wrappedFile.toNioPath();
  }

  @Override
  public @NotNull CharSequence getNameSequence() {
    return wrappedFile.getNameSequence();
  }

  @Override
  public boolean isCaseSensitive() {
    return wrappedFile.isCaseSensitive();
  }

  @Override
  public @NotNull String getPresentableName() {
    return wrappedFile.getPresentableName();
  }

  @Override
  public boolean isRecursiveOrCircularSymlink() {
    return wrappedFile.isRecursiveOrCircularSymlink();
  }

  @Override
  public <T> T computeWithPreloadedContentHint(byte @NotNull [] preloadedContentHint, @NotNull Supplier<? extends T> computable) {
    return wrappedFile.computeWithPreloadedContentHint(preloadedContentHint, computable);
  }


  @Override
  public @NotNull VirtualFile createChildData(Object requestor, @NotNull String name) throws IOException {
    return wrappedFile.createChildData(requestor, name);
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull String name) throws IOException {
    return wrappedFile.createChildDirectory(requestor, name);
  }

  @Override
  public @NotNull InputStream getInputStream() throws IOException {
    return wrappedFile.getInputStream();
  }

  @Override
  public @NotNull OutputStream getOutputStream(Object requestor,
                                               long newModificationStamp,
                                               long newTimeStamp) throws IOException {
    return wrappedFile.getOutputStream(requestor, newModificationStamp, newTimeStamp);
  }

  @Override
  public byte @NotNull [] contentsToByteArray() throws IOException {
    return wrappedFile.contentsToByteArray();
  }

  @Override
  public long getTimeStamp() {
    return wrappedFile.getTimeStamp();
  }

  @Override
  public long getLength() {
    return wrappedFile.getLength();
  }

  @Override
  public @NotNull Charset getCharset() {
    return wrappedFile.getCharset();
  }

  @Override
  public void setCharset(Charset charset) {
    wrappedFile.setCharset(charset);
  }

  @Override
  public void setCharset(Charset charset, @Nullable Runnable whenChanged) {
    wrappedFile.setCharset(charset, whenChanged);
  }

  @Override
  public void setCharset(Charset charset, @Nullable Runnable whenChanged, boolean fireEventsWhenChanged) {
    wrappedFile.setCharset(charset, whenChanged, fireEventsWhenChanged);
  }

  @Override
  protected void storeCharset(Charset charset) {
    super.storeCharset(charset);
  }

  @Override
  public @NotNull VirtualFile copy(Object requestor,
                                   @NotNull VirtualFile newParent,
                                   @NotNull String copyName) throws IOException {
    return wrappedFile.copy(requestor, newParent, copyName);
  }

  @Override
  public void rename(Object requestor, @NotNull String newName) throws IOException {
    wrappedFile.rename(requestor, newName);
  }

  @Override
  public void move(Object requestor, @NotNull VirtualFile newParent) throws IOException {
    wrappedFile.move(requestor, newParent);
  }

  @Override
  public void delete(Object requestor) throws IOException {
    wrappedFile.delete(requestor);
  }

  @Override
  public void setBOM(byte @Nullable [] BOM) {
    wrappedFile.setBOM(BOM);
  }

  @Override
  public byte @Nullable [] getBOM() {
    return wrappedFile.getBOM();
  }

  @Override
  public boolean isCharsetSet() {
    return wrappedFile.isCharsetSet();
  }

  @Override
  public long getModificationCount() {
    return wrappedFile.getModificationCount();
  }

  @Override
  public long getModificationStamp() {
    return wrappedFile.getModificationStamp();
  }

  @Override
  public boolean isInLocalFileSystem() {
    return wrappedFile.isInLocalFileSystem();
  }

  @Override
  public void setBinaryContent(byte @NotNull [] content,
                               long newModificationStamp,
                               long newTimeStamp) throws IOException {
    wrappedFile.setBinaryContent(content, newModificationStamp, newTimeStamp);
  }

  @Override
  public void setBinaryContent(byte @NotNull [] content,
                               long newModificationStamp,
                               long newTimeStamp,
                               Object requestor) throws IOException {
    wrappedFile.setBinaryContent(content, newModificationStamp, newTimeStamp, requestor);
  }

  @Override
  public byte @NotNull [] contentsToByteArray(boolean cacheContent) throws IOException {
    return wrappedFile.contentsToByteArray(cacheContent);
  }

  @Override
  public @Nullable String getDetectedLineSeparator() {
    return wrappedFile.getDetectedLineSeparator();
  }

  @Override
  public void setDetectedLineSeparator(@Nullable String separator) {
    wrappedFile.setDetectedLineSeparator(separator);
  }


  @Override
  public void refresh(boolean asynchronous, boolean recursive, @Nullable Runnable postRunnable) {
    wrappedFile.refresh(asynchronous, recursive, postRunnable);
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive) {
    //TODO RC: if we decide to cache children, we should refresh the cache (i.e. drop it) too
    wrappedFile.refresh(asynchronous, recursive);
  }

  //</editor-fold> =========================================================================================================================

  //<editor-fold desc="UserDataHolder overrides: prohibit access"> =========================================================================

  //Same approach as in TransientVirtualFileImpl: under the feature flag log an error on userData access.
  // Contrary to TransientVirtualFileImpl, here it is _possible_ to implement UserDataHolder methods correctly by delegating
  // them to wrappedFile -- but it will be inconsistent with TransientVirtualFileImpl behavior, and since TransientVirtualFileImpl
  // and CacheAvoidingVirtualFileWrapper are used in the same context, such difference would be a violation of 'least-surprise'
  // principle. I.e., client doesn't know if the file returned by VirtualFileManager.findCachedOrTransientFileByPath() is cached
  // or not, it could be either TransientVirtualFileImpl or CacheAvoidingVirtualFileWrapper, and both should behave +/- the same
  // way.

  @Override
  public void copyUserDataTo(@NotNull UserDataHolderBase other) {
    logUnsupportedIfNeeded(TransientVirtualFileImpl.LOG_USER_DATA_HOLDER_ACCESS);
    wrappedFile.copyUserDataTo(other);
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    logUnsupportedIfNeeded(TransientVirtualFileImpl.LOG_USER_DATA_HOLDER_ACCESS);
    return wrappedFile.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    logUnsupportedIfNeeded(TransientVirtualFileImpl.LOG_USER_DATA_HOLDER_ACCESS);
    wrappedFile.putUserData(key, value);
  }

  @Override
  public <T> @UnknownNullability T getCopyableUserData(@NotNull Key<T> key) {
    logUnsupportedIfNeeded(TransientVirtualFileImpl.LOG_USER_DATA_HOLDER_ACCESS);
    return wrappedFile.getCopyableUserData(key);
  }

  @Override
  public <T> void putCopyableUserData(@NotNull Key<T> key, T value) {
    logUnsupportedIfNeeded(TransientVirtualFileImpl.LOG_USER_DATA_HOLDER_ACCESS);
    wrappedFile.putCopyableUserData(key, value);
  }

  @Override
  public <T> boolean replace(@NotNull Key<T> key, @Nullable T oldValue, @Nullable T newValue) {
    logUnsupportedIfNeeded(TransientVirtualFileImpl.LOG_USER_DATA_HOLDER_ACCESS);
    return wrappedFile.replace(key, oldValue, newValue);
  }

  @Override
  public <T> @NotNull T putUserDataIfAbsent(@NotNull Key<T> key, @NotNull T value) {
    logUnsupportedIfNeeded(TransientVirtualFileImpl.LOG_USER_DATA_HOLDER_ACCESS);
    return wrappedFile.putUserDataIfAbsent(key, value);
  }

  @Override
  public void copyCopyableDataTo(@NotNull UserDataHolderBase clone) {
    logUnsupportedIfNeeded(TransientVirtualFileImpl.LOG_USER_DATA_HOLDER_ACCESS);
    wrappedFile.copyCopyableDataTo(clone);
  }

  @Override
  public boolean isCopyableDataEqual(@NotNull UserDataHolderBase other) {
    logUnsupportedIfNeeded(TransientVirtualFileImpl.LOG_USER_DATA_HOLDER_ACCESS);
    return wrappedFile.isCopyableDataEqual(other);
  }

  @Override
  public boolean isUserDataEmpty() {
    logUnsupportedIfNeeded(TransientVirtualFileImpl.LOG_USER_DATA_HOLDER_ACCESS);
    return wrappedFile.isUserDataEmpty();
  }

  private static void logUnsupportedIfNeeded(boolean logUserDataHolderAccess) {
    if (logUserDataHolderAccess) {
      //will also log a stacktrace
      LOG.error("CacheAvoidingVirtualFileWrapper does NOT really support UserDataHolder (see javadoc)");
    }
  }

  //</editor-fold> =========================================================================================================================


  @Override
  public int hashCode() {
    return wrappedFile.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (!(o instanceof VirtualFileWithId)) {
      return false;
    }

    //untrivial equals implementation: all VirtualFileWithId implementations are considered comparable -- even if they
    // are completely different implementation classes

    return ((VirtualFileWithId)o).getId() == wrappedFile.getId();
  }

  @Override
  public String toString() {
    return "CacheAvoidingVirtualFileWrapper[" + wrappedFile + "]";
  }
}
