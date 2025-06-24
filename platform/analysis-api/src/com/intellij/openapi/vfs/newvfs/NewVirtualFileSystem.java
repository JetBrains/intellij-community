// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 'New' virtual file systems are managed and centered around {@link ManagingFS}.
 * These are file systems that are really data-providers ({@link FileSystemInterface}) for {@link ManagingFS},
 * while {@link ManagingFS} caches ({@link CachingVirtualFileSystem}) and manages the data provided.
 */
public abstract class NewVirtualFileSystem extends VirtualFileSystem implements FileSystemInterface, CachingVirtualFileSystem {
  private static final Logger LOG = Logger.getInstance(NewVirtualFileSystem.class);

  private final Map<VirtualFileListener, VirtualFileListener> myListenerWrappers = new ConcurrentHashMap<>();

  /**
   * <p>Implementations <b>should</b> convert separator chars to forward slashes and remove duplicates ones,
   * and convert paths to "absolute" form (so that they start from a root that is valid for this FS and
   * could be later extracted with {@link #extractRootPath}).</p>
   *
   * <p>Implementations <b>should not</b> normalize paths by eliminating directory traversals or other indirections.</p>
   *
   * @return a normalized path, or {@code null} when a path is invalid for this FS.
   */
  @ApiStatus.OverrideOnly
  protected @Nullable String normalize(@NotNull String path) {
    return path;
  }

  /**
   * IntelliJ platform calls this method with non-null value returned by {@link #normalize}, but if something went wrong
   * and an implementation can't extract a valid root path nevertheless, it should return an empty string.
   *
   * @return a root path, if a normalizedPath is recognizable by the current FileSystem, or empty string if not.
   */
  @ApiStatus.OverrideOnly
  protected abstract @NotNull String extractRootPath(@NotNull String normalizedPath);

  public abstract @Nullable VirtualFile findFileByPathIfCached(@NonNls @NotNull String path);

  @Override
  public void refreshWithoutFileWatcher(boolean asynchronous) {
    refresh(asynchronous);
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public boolean isSymLink(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public String resolveSymLink(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
    VirtualFileListener wrapper = new VirtualFileFilteringListener(listener, this);
    //noinspection deprecation
    VirtualFileManager.getInstance().addVirtualFileListener(wrapper);
    myListenerWrappers.put(listener, wrapper);
  }

  @Override
  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
    VirtualFileListener wrapper = myListenerWrappers.remove(listener);
    if (wrapper != null) {
      //noinspection deprecation
      VirtualFileManager.getInstance().removeVirtualFileListener(wrapper);
    }
  }

  /** Provides a way to sort file systems */
  //TODO it seems this method is never really used -- maybe drop it (deprecate-for-removal?)
  public abstract int getRank();

  @Override
  public abstract @NotNull VirtualFile copyFile(Object requestor,
                                                @NotNull VirtualFile file,
                                                @NotNull VirtualFile newParent,
                                                @NotNull String newName) throws IOException;

  @Override
  public abstract @NotNull VirtualFile createChildDirectory(Object requestor,
                                                            @NotNull VirtualFile parent,
                                                            @NotNull String name) throws IOException;

  @Override
  public abstract @NotNull VirtualFile createChildFile(Object requestor,
                                                       @NotNull VirtualFile parent,
                                                       @NotNull String name) throws IOException;

  @Override
  public abstract void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException;

  @Override
  public abstract void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException;

  @Override
  public abstract void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException;

  public boolean markNewFilesAsDirty() {
    return false;
  }

  //MAYBE RC: change signature (file) -> (parent, fileName)? no need to create a FakeVirtualFile
  public @NotNull String getCanonicallyCasedName(@NotNull VirtualFile file) {
    return file.getName();
  }

  /**
   * Reads various file attributes in one shot (to reduce the number of native I/O calls).
   *
   * @param file file to get attributes of.
   * @return attributes of a given file, or {@code null} if the file doesn't exist.
   */
  public abstract @Nullable FileAttributes getAttributes(@NotNull VirtualFile file);

  /**
   * Returns {@code true} if {@code path} represents a directory with at least one child.
   * Override if your file system can answer this question more efficiently (without listing all children).
   */
  public boolean hasChildren(@NotNull VirtualFile file) {
    return list(file).length != 0;
  }

  /**
   * Resolves the given path against the given fileSystem, without adding the file in VFS cache.
   *
   * @return a VFS cached file for the path given, if the file is already cached by VFS, or a transient file for the path, if such
   * a file is not yet cached in VFS, or null if the path is invalid or doesn't exist, or doesn't belong to the fileSystem given.
   */
  @Override
  @ApiStatus.Internal
  public @Nullable VirtualFile findFileByPathWithoutCaching(@NotNull String path) {
    Pair<VirtualFile, VirtualFile> pair = findCachedOrTransientFileByPath(this, path);
    if (pair.first != null) {
      return pair.first;
    }
    else if (pair.second != null) {
      return pair.second;
    }
    return null;//file is invalid, doesn't exist, or doesn't belong to this file system
  }

  /* ============================================== static helpers ============================================== */


  /**
   * Resolves the given path against the given fileSystem.
   *
   * @return VirtualFile for the path, if resolved, null if not -- e.g. path is invalid or doesn't exist, or not belongs to
   * fileSystem given.
   */
  protected static @Nullable NewVirtualFile findFileByPath(@NotNull NewVirtualFileSystem fileSystem,
                                                           @NotNull String path) {
    Pair<NewVirtualFile, Iterable<String>> rootAndPath = extractRootAndPathSegments(fileSystem, path);
    if (rootAndPath == null) return null;

    NewVirtualFile file = rootAndPath.first;
    for (String pathElement : rootAndPath.second) {
      if (pathElement.isEmpty() || ".".equals(pathElement)) continue;
      NewVirtualFile fileBefore = file;
      //Here we do 'partial canonicalization' of the path: we resolve symlinks, but only before '..' segments.
      // It makes the resolution a bit surprising: 'a/b/c' and 'a/../a/b/c' could be resolved to different files, if 'a'
      // is a symlink. But this follows the POSIX rules for path resolution, there symlinks indeed resolved as soon, as
      // they are met in the path.
      //Still, the result differs from regular canonicalization, via VirtualFile.getCanonicalPath() or Path.toRealPath(),
      // there _all_ symlinks are resolved -- and the result also differs from Path.toRealPath(NOFOLLOW_LINKS) where _none_
      // of symlinks are resolved.
      if ("..".equals(pathElement)) {
        if (file.is(VFileProperty.SYMLINK)) {
          NewVirtualFile canonicalFile = file.getCanonicalFile();
          if (LOG.isDebugEnabled()) {
            LOG.debug("[" + file.getPath() + "]: symlink to [" + canonicalFile + "]");
          }
          file = canonicalFile != null ? canonicalFile.getParent() : null;
        }
        else {
          file = file.getParent();
        }
      }
      else {
        file = file.findChild(pathElement);
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("[" + fileBefore.getPath() + "]/[" + pathElement + "] resolved to [" + file + "]");
      }

      if (file == null) return null;
    }

    return file;
  }

  /**
   * Resolves the given path against the given fileSystem, looking only in already cached in VFS {@link VirtualFile}s.
   * The difference from {@link #findFileByPath(NewVirtualFileSystem, String)} is that this method looks only in already cached
   * in VFS {@link VirtualFile}s -- i.e. it doesn't load new file-tree branches/leaves into the VFS during resolve.
   * If cached VFS files are enough to resolve the path, returns {@code Pair(resolvedVirtualFile, null)}, but if VFS files are
   * NOT enough to resolve the path, returns {@code Pair(null, lastCachedFile)} -- i.e. the last successfully resolved
   * file-tree branch/leaf along the given path.
   * Method returns {@code Pair(null, null)} if the path is invalid or doesn't exist, or doesn't belong to the fileSystem given.
   * <br/>
   * This method is often used to either get the resolved and already cached file, or call VFS.refresh() or alike starting with
   * lastCachedFile, if a subtree is not yet resolved.
   */
  public static @NotNull Pair<NewVirtualFile, NewVirtualFile> findCachedFileByPath(@NotNull NewVirtualFileSystem fileSystem,
                                                                                   @NotNull String path) {
    Pair<NewVirtualFile, Iterable<String>> rootAndPath = extractRootAndPathSegments(fileSystem, path);
    if (rootAndPath == null) return Pair.empty();

    NewVirtualFile root = rootAndPath.first;
    Iterable<String> pathSegments = rootAndPath.second;
    NewVirtualFile currentFile = root;
    for (String pathElement : pathSegments) {
      if (pathElement.isEmpty() || ".".equals(pathElement)) continue;

      NewVirtualFile last = currentFile;
      if ("..".equals(pathElement)) {
        currentFile = fetchParentResolvingSymlink(fileSystem, currentFile);
      }
      else {
        currentFile = currentFile.findChildIfCached(pathElement);
      }

      if (currentFile == null) {
        return Pair.create(null, last);
      }
    }

    return Pair.create(currentFile, null);
  }

  /**
   * Resolves the given path against the given fileSystem.
   *
   * @return (cachedFile, null), or (null, transientFile) for the path given, or (null, null) if the path is invalid or doesn't exist,
   * or doesn't belong to the fileSystem given.
   */
  @ApiStatus.Internal
  public static @NotNull Pair<VirtualFile, VirtualFile> findCachedOrTransientFileByPath(@NotNull NewVirtualFileSystem fileSystem,
                                                                                        @NotNull String path) {
    Pair<NewVirtualFile, Iterable<String>> rootAndPath = extractRootAndPathSegments(fileSystem, path);
    if (rootAndPath == null) return Pair.empty();

    NewVirtualFile root = rootAndPath.first;
    Iterable<String> pathSegments = rootAndPath.second;
    VirtualFile currentFile = root;
    for (String pathElement : pathSegments) {
      if (pathElement.isEmpty() || ".".equals(pathElement)) continue;

      if ("..".equals(pathElement)) {
        currentFile = fetchParentResolvingSymlink(fileSystem, root);
      }
      else {
        if (currentFile instanceof NewVirtualFile) {
          VirtualFile child = ((NewVirtualFile)currentFile).findChildIfCached(pathElement);
          if (child != null) {
            currentFile = child;
          }
          else {
            currentFile = new TransientVirtualFileImpl(pathElement, path, fileSystem, currentFile);
          }
        }
        else {
          currentFile = new TransientVirtualFileImpl(pathElement, path, fileSystem, currentFile);
        }
      }

      if (currentFile == null) {
        break;
      }
    }

    if (currentFile instanceof NewVirtualFile) {
      VirtualFile wrappedFile = new CacheAvoidingVirtualFileWrapper((NewVirtualFile)currentFile);
      return Pair.create(wrappedFile, null);
    }
    else {//TransientVirtualFileImpl is NOT NewVirtualFile
      return Pair.create(null, currentFile);
    }
  }

  /**
   * @return cached VirtualFile for the given path, or null if the path can't be resolved/invalid, not belongs to the fileSystem given,
   * or not yet cached in VFS.
   */
  @ApiStatus.Internal
  public static @Nullable NewVirtualFile findFileByPathIfCached(@NotNull NewVirtualFileSystem fileSystem,
                                                                @NotNull String path) {
    return findCachedFileByPath(fileSystem, path).first;
  }

  /**
   * @return just {@link VirtualFile#getParent()} if the file is a regular file (not a symlink). If the file is a symlink, than
   * canonicalise it, and get parent of the canonicalised file.
   */
  private static @Nullable NewVirtualFile fetchParentResolvingSymlink(@NotNull NewVirtualFileSystem fileSystem,
                                                                      @NotNull NewVirtualFile file) {
    if (file.is(VFileProperty.SYMLINK)) {
      String canonicalPath = file.getCanonicalPath();
      NewVirtualFile canonicalFile = canonicalPath != null ? findCachedFileByPath(fileSystem, canonicalPath).first : null;
      return canonicalFile != null ? canonicalFile.getParent() : null;
    }
    else {
      return file.getParent();
    }
  }

  private static final String FILE_SEPARATORS = "/" + (File.separatorChar == '/' ? "" : File.separator);

  /**
   * Same as {@link #extractRootFromPath(NewVirtualFileSystem, String)}, but path-from-root returned not as a single String,
   * but as an {@link Iterable} of the path's segments.
   */
  @ApiStatus.Internal
  public static @Nullable Pair<@NotNull NewVirtualFile, @NotNull Iterable<String>> extractRootAndPathSegments(@NotNull NewVirtualFileSystem fileSystem,
                                                                                                              @NotNull String path) {
    PathFromRoot pair = extractRootFromPath(fileSystem, path);
    if (pair == null) return null;
    Iterable<String> parts = StringUtil.tokenize(pair.pathFromRoot(), FILE_SEPARATORS);
    return Pair.create(pair.root(), parts);
  }

  /**
   * Returns a (file system root, relative path inside that root) pair, or {@code null} when the path is invalid or the root is not found.
   * <br/>
   * For example:
   * <pre>
   * extractRootFromPath(LocalFileSystem.getInstance, "C:/temp") -> (VirtualFile("C:"), "/temp")
   * extractRootFromPath(JarFileSystem.getInstance, "/temp/temp.jar!/com/foo/bar") -> (VirtualFile("/temp/temp.jar!/"), "/com/foo/bar")
   * </pre>
   * <p>
   * Returns null if the path is incorrect and/or not recognizable by the fileSystem
   */
  @ApiStatus.Internal
  public static @Nullable PathFromRoot extractRootFromPath(@NotNull NewVirtualFileSystem fileSystem,
                                                           @NotNull String path) {
    String normalizedPath = fileSystem.normalize(path);
    if (normalizedPath == null || normalizedPath.isBlank()) {
      return null;
    }

    String rootPath = fileSystem.extractRootPath(normalizedPath);
    if (rootPath.isBlank() || rootPath.length() > normalizedPath.length()) {
      //rootPath.isBlank() means that it is the path that is incorrect (not belong to the file system)
      LOG.warn(fileSystem + " has extracted incorrect root '" + rootPath + "' from '" + normalizedPath +
               "' (original '" + path + "')");
      return null;
    }

    NewVirtualFile root = ManagingFS.getInstance().findRoot(rootPath, fileSystem);
    if (root == null || !root.exists()) {
      return null;
    }

    int restPathStart = rootPath.length();
    if (restPathStart < normalizedPath.length() && normalizedPath.charAt(restPathStart) == '/') restPathStart++;
    return new PathFromRoot(root, normalizedPath.substring(restPathStart));
  }

  @ApiStatus.Internal
  public record PathFromRoot(@NotNull NewVirtualFile root, @NotNull String pathFromRoot) {
  }
}
