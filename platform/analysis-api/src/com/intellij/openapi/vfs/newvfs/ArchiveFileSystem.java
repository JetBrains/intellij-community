// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.newvfs.persistent.BatchingFileSystem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.util.containers.CollectionFactory.createFilePathMap;
import static com.intellij.util.containers.CollectionFactory.createFilePathSet;

/**
 * Common interface of archive-based file systems (jar://, phar://, etc.).
 */
public abstract class ArchiveFileSystem extends NewVirtualFileSystem implements BatchingFileSystem {
  private static final Key<VirtualFile> LOCAL_FILE = Key.create("vfs.archive.local.file");

  /**
   * Returns a root entry of an archive hosted by a given local file
   * (i.e.: file:///path/to/jar.jar => jar:///path/to/jar.jar!/),
   * or {@code null} if the file does not host this file system.
   */
  public @Nullable VirtualFile getRootByLocal(@NotNull VirtualFile file) {
    return isCorrectFileType(file) ? findFileByPath(getRootPathByLocal(file)) : null;
  }

  /**
   * Dresses a local file path to make it a suitable root path for this filesystem.
   * E.g., VirtualFile("/x/y.jar") -> "/x/y.jar!/"
   */
  public @NotNull String getRootPathByLocal(@NotNull VirtualFile file) {
    return composeRootPath(file.getPath());
  }

  /**
   * Returns a root entry of an archive which hosts a given entry file
   * (i.e.: jar:///path/to/jar.jar!/resource.xml => jar:///path/to/jar.jar!/),
   * or {@code null} if the file does not belong to this file system.
   */
  public @Nullable VirtualFile getRootByEntry(@NotNull VirtualFile entry) {
    return entry.getFileSystem() == this ? VfsUtilCore.getRootFile(entry) : null;
  }

  /**
   * Returns a local file of an archive which hosts a given entry file
   * (i.e.: jar:///path/to/jar.jar!/resource.xml => file:///path/to/jar.jar),
   * or {@code null} if the file does not belong to this file system.
   */
  public @Nullable VirtualFile getLocalByEntry(@NotNull VirtualFile entry) {
    if (entry.getFileSystem() != this) return null;

    VirtualFile root = getRootByEntry(entry);
    assert root != null : entry;

    VirtualFile local = LOCAL_FILE.get(root);
    if (local == null) {
      String localPath = extractLocalPath(root.getPath());
      local = StandardFileSystems.local().findFileByPath(localPath);
      if (local != null) LOCAL_FILE.set(root, local);
    }
    return local;
  }

  /**
   * Strips any separator chars from a root path (obtained via {@link VfsUtilCore#getRootFile}) to obtain a path to a local file.
   */
  protected abstract @NotNull String extractLocalPath(@NotNull String rootPath);

  /**
   * A reverse to {@link #extractLocalPath(String)} - i.e. dresses a local file path to make it a suitable root path for this filesystem.
   * E.g., "/x/y.jar" -> "/x/y.jar!/"
   */
  protected abstract @NotNull String composeRootPath(@NotNull String localPath);

  protected abstract @NotNull ArchiveHandler getHandler(@NotNull VirtualFile entryFile);

  // standard implementations

  @Override
  public int getRank() {
    return LocalFileSystem.getInstance().getRank() + 1;
  }

  @Override
  public @NotNull VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent, @NotNull String copyName) throws IOException {
    throw new IOException(AnalysisBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String dir) throws IOException {
    throw new IOException(AnalysisBundle.message("jar.modification.not.supported.error", parent.getUrl()));
  }

  @Override
  public @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String file) throws IOException {
    throw new IOException(AnalysisBundle.message("jar.modification.not.supported.error", parent.getUrl()));
  }

  @Override
  public void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException {
    throw new IOException(AnalysisBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    throw new IOException(AnalysisBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException {
    throw new IOException(AnalysisBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  protected @NotNull String getRelativePath(@NotNull VirtualFile file) {
    String relativePath = file.getPath().substring(VfsUtilCore.getRootFile(file).getPath().length());
    return StringUtil.trimLeading(relativePath, '/');
  }

  private final Function<VirtualFile, FileAttributes> myAttrGetter = ManagingFS.getInstance().accessDiskWithCheckCanceled(
    file -> getHandler(file).getAttributes(getRelativePath(file))
  );

  @Override
  public @Nullable FileAttributes getAttributes(@NotNull VirtualFile file) {
    return myAttrGetter.apply(file);
  }

  private final Function<Pair<VirtualFile, Set<String>>, Map<@NotNull String, @NotNull FileAttributes>> myListWithAttrGetter =
    ManagingFS.getInstance().accessDiskWithCheckCanceled(
      dirAndNames -> {
        VirtualFile dir = dirAndNames.first;
        Set<String> childNames = dirAndNames.second;

        return childrenWithAttributes(dir, childNames);
      }
    );

  private @NotNull Map<@NotNull String, @NotNull FileAttributes> childrenWithAttributes(@NotNull VirtualFile dir,
                                                                                        @Nullable Set<String> childNames) {
    String directoryRelativePath = getRelativePath(dir);
    String normalizedDirectoryPath = directoryRelativePath.isEmpty() ?
                                     "" :
                                     StringUtil.trimTrailing(directoryRelativePath, '/') + '/';

    ArchiveHandler handler = getHandler(dir);
    if (childNames == null) {
      childNames = createFilePathSet(handler.list(directoryRelativePath), isCaseSensitive());
    }

    Map<String, FileAttributes> childrenWithAttributes = createFilePathMap(childNames.size(), isCaseSensitive());

    for (String childName : childNames) {
      String childRelativePath = normalizedDirectoryPath + childName;
      FileAttributes childAttributes = handler.getAttributes(childRelativePath);
      if (childAttributes != null) {
        childrenWithAttributes.put(childName, childAttributes);
      }
    }
    return childrenWithAttributes;
  }

  @Override
  @ApiStatus.Internal
  public @NotNull Map<@NotNull String, @NotNull FileAttributes> listWithAttributes(@NotNull VirtualFile dir,
                                                                                   @Nullable Set<String> childrenNames) {
    if (childrenNames != null && childrenNames.isEmpty()) {
      return Collections.emptyMap();
    }
    return myListWithAttrGetter.apply(new Pair<>(dir, childrenNames));
  }

  @ApiStatus.Internal
  public @NotNull FileAttributes getArchiveRootAttributes(@NotNull VirtualFile file) {
    // Hack for handler initialization to have a caching e.g. JarFileSystemImpl.getHandler
    getHandler(file);
    return ArchiveHandler.DIRECTORY_ATTRIBUTES;
  }

  private final Function<VirtualFile, String[]> myChildrenGetter =
    ManagingFS.getInstance().accessDiskWithCheckCanceled(file -> getHandler(file).list(getRelativePath(file)));

  @Override
  public String @NotNull [] list(@NotNull VirtualFile file) {
    return myChildrenGetter.apply(file);
  }

  @Override
  public boolean exists(@NotNull VirtualFile file) {
    return file.getParent() == null ? getLocalByEntry(file) != null : getAttributes(file) != null;
  }

  @Override
  public boolean isDirectory(@NotNull VirtualFile file) {
    if (file.getParent() == null) return true;
    FileAttributes attributes = getAttributes(file);
    return attributes == null || attributes.isDirectory();
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public long getTimeStamp(@NotNull VirtualFile file) {
    if (file.getParent() == null) {
      VirtualFile host = getLocalByEntry(file);
      if (host != null) return host.getTimeStamp();
    }
    else {
      FileAttributes attributes = getAttributes(file);
      if (attributes != null) return attributes.lastModified;
    }
    return ArchiveHandler.DEFAULT_TIMESTAMP;
  }

  @Override
  public long getLength(@NotNull VirtualFile file) {
    if (file.getParent() == null) {
      VirtualFile host = getLocalByEntry(file);
      if (host != null) return host.getLength();
    }
    else {
      FileAttributes attributes = getAttributes(file);
      if (attributes != null) return attributes.length;
    }
    return ArchiveHandler.DEFAULT_LENGTH;
  }

  private final Function<VirtualFile, Pair<byte[], IOException>> myContentGetter =
    ManagingFS.getInstance().accessDiskWithCheckCanceled(file -> {
      try {
        return pair(getHandler(file).contentsToByteArray(getRelativePath(file)), null);
      }
      catch (IOException e) {
        return pair(null, e);
      }
    });

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    Pair<byte[], IOException> pair = myContentGetter.apply(file);
    IOException exception = pair.second;
    if (exception != null) {
      exception.addSuppressed(new Throwable("Caller thread's stacktrace"));
      throw exception;
    }
    return pair.first;
  }

  @Override
  public @NotNull InputStream getInputStream(@NotNull VirtualFile file) throws IOException {
    return getHandler(file).getInputStream(getRelativePath(file));
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long timeStamp) throws IOException {
    throw new IOException(AnalysisBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) throws IOException {
    throw new IOException(AnalysisBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  @Override
  public @NotNull OutputStream getOutputStream(@NotNull VirtualFile file, Object requestor, long modStamp, long timeStamp) throws IOException {
    throw new IOException(AnalysisBundle.message("jar.modification.not.supported.error", file.getUrl()));
  }

  // service methods

  /**
   * Returns a local file of an archive which hosts a root with the given path
   * (i.e.: "jar:///path/to/jar.jar!/" => file:///path/to/jar.jar),
   * or {@code null} if the local file is of incorrect type.
   */
  public @Nullable VirtualFile findLocalByRootPath(@NotNull String rootPath) {
    String localPath = extractLocalPath(rootPath);
    VirtualFile local = StandardFileSystems.local().findFileByPath(localPath);
    return local != null && isCorrectFileType(local) ? local : null;
  }

  /**
   * Implementations should return {@code false} if the given file may not host this file system.
   */
  protected boolean isCorrectFileType(@NotNull VirtualFile local) {
    return FileTypeRegistry.getInstance().getFileTypeByFileName(local.getNameSequence()) == ArchiveFileType.INSTANCE;
  }

  @ApiStatus.Internal
  public final void clearArchiveCache(@NotNull VirtualFile sampleEntry) {
    getHandler(sampleEntry).clearCaches();
  }

  @ApiStatus.Internal
  public static @NotNull String getLocalPath(@NotNull ArchiveFileSystem vfs, @NotNull String entryPath) {
    return vfs.extractLocalPath(entryPath);
  }

  @ApiStatus.Internal
  public static @NotNull String composeRootPath(@NotNull ArchiveFileSystem fileSystem, @NotNull String localPath) {
    return fileSystem.composeRootPath(localPath);
  }
}
