// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;

public final class VfsUtil extends VfsUtilCore {
  private static final Logger LOG = Logger.getInstance(VfsUtil.class);

  /**
   * Specifies an average delay between a file system event and a moment the IDE gets pinged about it by fsnotifier.
   */
  public static final long NOTIFICATION_DELAY_MILLIS = 300;

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static void saveText(@NotNull VirtualFile file, @NotNull String text) throws IOException {
    VfsUtilCore.saveText(file, text);
  }

  public static byte @NotNull [] toByteArray(@NotNull VirtualFile file, @NotNull String text) throws IOException {
    if (text.isEmpty()) {
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }

    Charset charset = file.getCharset();
    return text.getBytes(charset);
  }

  /**
   * Copies all files matching the {@code filter} from {@code fromDir} to {@code toDir}.
   * Symlinks end special files are ignored.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if {@code requestor} is {@code null}.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param fromDir   the directory to copy from
   * @param toDir     the directory to copy to
   * @param filter    {@link VirtualFileFilter}
   * @throws IOException if files failed to be copied
   */
  public static void copyDirectory(Object requestor,
                                   @NotNull VirtualFile fromDir,
                                   @NotNull VirtualFile toDir,
                                   @Nullable VirtualFileFilter filter) throws IOException {
    @SuppressWarnings("UnsafeVfsRecursion") VirtualFile[] children = fromDir.getChildren();
    for (VirtualFile child : children) {
      if (!child.is(VFileProperty.SYMLINK) && !child.is(VFileProperty.SPECIAL) && (filter == null || filter.accept(child))) {
        if (!child.isDirectory()) {
          copyFile(requestor, child, toDir);
        }
        else {
          VirtualFile newChild = toDir.findChild(child.getName());
          if (newChild == null) {
            newChild = toDir.createChildDirectory(requestor, child.getName());
          }
          copyDirectory(requestor, child, newChild, filter);
        }
      }
    }
  }

  /**
   * Makes a copy of the {@code file} in the {@code toDir} folder and returns it.
   * Handles both files and directories.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if {@code requestor} is {@code null}.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param file      file or directory to make a copy of
   * @param toDir     directory to make a copy in
   * @throws IOException if file failed to be copied
   */
  public static @NotNull VirtualFile copy(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile toDir) throws IOException {
    if (file.isDirectory()) {
      VirtualFile newDir = toDir.createChildDirectory(requestor, file.getName());
      copyDirectory(requestor, file, newDir, null);
      return newDir;
    }
    return copyFile(requestor, file, toDir);
  }

  /**
   * Gets the array of common ancestors for passed files.
   *
   * @param files array of files
   * @return array of common ancestors for passed files
   */
  public static VirtualFile @NotNull [] getCommonAncestors(VirtualFile @NotNull [] files) {
    // Separate files by first component in the path.
    Map<VirtualFile, Set<VirtualFile>> map = new HashMap<>();
    for (VirtualFile aFile : files) {
      VirtualFile directory = aFile.isDirectory() ? aFile : aFile.getParent();
      if (directory == null) return VirtualFile.EMPTY_ARRAY;
      VirtualFile[] path = getPathComponents(directory);
      Set<VirtualFile> filesSet;
      final VirtualFile firstPart = path[0];
      if (map.containsKey(firstPart)) {
        filesSet = map.get(firstPart);
      }
      else {
        filesSet = new HashSet<>();
        map.put(firstPart, filesSet);
      }
      filesSet.add(directory);
    }
    // Find a common ancestor for each set of files.
    List<VirtualFile> ancestorsList = new ArrayList<>();
    for (Set<VirtualFile> filesSet : map.values()) {
      VirtualFile ancestor = null;
      for (VirtualFile file : filesSet) {
        if (ancestor == null) {
          ancestor = file;
          continue;
        }
        ancestor = getCommonAncestor(ancestor, file);
      }
      ancestorsList.add(ancestor);
      filesSet.clear();
    }
    return toVirtualFileArray(ancestorsList);
  }

  /**
   * Gets the common ancestor for passed files, or {@code null} if the files do not have common ancestors.
   */
  public static @Nullable VirtualFile getCommonAncestor(@NotNull Collection<? extends VirtualFile> files) {
    VirtualFile ancestor = null;
    for (VirtualFile file : files) {
      if (ancestor == null) {
        ancestor = file;
      }
      else {
        ancestor = getCommonAncestor(ancestor, file);
        if (ancestor == null) return null;
      }
    }
    return ancestor;
  }

  public static @Nullable VirtualFile findRelativeFile(@Nullable VirtualFile base, String @NotNull ... path) {
    VirtualFile file = base;

    for (String pathElement : path) {
      if (file == null) return null;
      if ("..".equals(pathElement)) {
        file = file.getParent();
      }
      else {
        file = file.findChild(pathElement);
      }
    }

    return file;
  }

  /**
   * Searches for the file specified by given URL.
   * Note that this method is currently tested only for "file" and "jar" protocols under Unix and Windows.
   *
   * @param url the URL to find file by
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   */
  public static @Nullable VirtualFile findFileByURL(@NotNull URL url) {
    String vfsUrl = convertFromUrl(url);
    return VirtualFileManager.getInstance().findFileByUrl(vfsUrl);
  }

  public static @Nullable VirtualFile findFile(@NotNull Path file, @Nullable Project project, boolean refreshIfNeeded) {
    return findFile(file.toAbsolutePath().toString().replace(File.separatorChar, '/'), project, refreshIfNeeded);
  }

  public static @Nullable VirtualFile findFile(@NotNull Path file, boolean refreshIfNeeded) {
    return findFile(file.toAbsolutePath().toString().replace(File.separatorChar, '/'), null, refreshIfNeeded);
  }

  public static @Nullable VirtualFile findFileByIoFile(@NotNull File file, boolean refreshIfNeeded) {
    return findFile(file.getAbsolutePath().replace(File.separatorChar, '/'), null, refreshIfNeeded);
  }

  private static @Nullable VirtualFile findFile(@NotNull String filePath, @Nullable Project project, boolean refreshIfNeeded) {
    VirtualFileSystem fileSystem = StandardFileSystems.local();
    VirtualFile virtualFile = findFile(filePath, refreshIfNeeded, fileSystem);
    if (virtualFile == null && project.getPresentableUrl() != null) {
      virtualFile = findFile(project.getPresentableUrl() + filePath, refreshIfNeeded, fileSystem);
    }
    return virtualFile;
  }

  @Nullable
  private static VirtualFile findFile(@NotNull String filePath, boolean refreshIfNeeded, VirtualFileSystem fileSystem) {
    VirtualFile virtualFile = fileSystem.findFileByPath(filePath);
    if (refreshIfNeeded && (virtualFile == null || !virtualFile.isValid())) {
      virtualFile = fileSystem.refreshAndFindFileByPath(filePath);
    }
    return virtualFile;
  }

  public static @Nullable VirtualFile refreshAndFindChild(@NotNull VirtualFile directory, @NotNull String name) {
    if (directory instanceof NewVirtualFile) {
      return ((NewVirtualFile)directory).refreshAndFindChild(name);
    }
    return findFileByIoFile(new File(virtualToIoFile(directory), name), true);
  }

  /**
   * @return correct URL; must be used only for external communication
   */
  public static @NotNull URI toUri(@NotNull File file) {
    String path = file.toURI().getPath();
    try {
      if (SystemInfo.isWindows && path.charAt(0) != '/') {
        path = '/' + path;
      }
      return new URI(StandardFileSystems.FILE_PROTOCOL, "", path, null, null);
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * URI - may be incorrect (escaped or missed "/" before the disk name on Windows, not fully encoded, may contain a query or a fragment)
   * @return correct URI; must be used only for external communication
   */
  public static @Nullable URI toUri(@NonNls @NotNull String uri) {
    int index = uri.indexOf("://");
    if (index < 0) {
      // true URI, like mailto:
      try {
        return new URI(uri);
      }
      catch (URISyntaxException e) {
        LOG.debug(e);
        return null;
      }
    }

    if (SystemInfo.isWindows && uri.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
      int firstSlashIndex = index + "://".length();
      if (uri.charAt(firstSlashIndex) != '/') {
        uri = LocalFileSystem.PROTOCOL_PREFIX + '/' + uri.substring(firstSlashIndex);
      }
    }

    try {
      return new URI(uri);
    }
    catch (URISyntaxException e) {
      LOG.debug("uri is not fully encoded", e);
      // so, uri is not fully encoded (space)
      try {
        int fragmentIndex = uri.lastIndexOf('#');
        String path = uri.substring(index + 1, fragmentIndex > 0 ? fragmentIndex : uri.length());
        String fragment = fragmentIndex > 0 ? uri.substring(fragmentIndex + 1) : null;
        return new URI(uri.substring(0, index), path, fragment);
      }
      catch (URISyntaxException e1) {
        LOG.debug(e1);
        return null;
      }
    }
  }

  public static @NotNull String getUrlForLibraryRoot(@NotNull File libraryRoot) {
    return getUrlForLibraryRoot(libraryRoot.getAbsolutePath(), libraryRoot.getName());
  }

  public static @NotNull String getUrlForLibraryRoot(@NotNull Path libraryRoot) {
    return getUrlForLibraryRoot(libraryRoot.toAbsolutePath().toString(), libraryRoot.getFileName().toString());
  }

  private static @NotNull String getUrlForLibraryRoot(@NotNull String libraryRootAbsolutePath, @NotNull String libraryRootFileName) {
    String path = FileUtil.toSystemIndependentName(libraryRootAbsolutePath);
    return FileTypeRegistry.getInstance().getFileTypeByFileName(libraryRootFileName) == ArchiveFileType.INSTANCE
           ? VirtualFileManager.constructUrl(StandardFileSystems.JAR_PROTOCOL, path + URLUtil.JAR_SEPARATOR)
           : VirtualFileManager.constructUrl(LocalFileSystem.getInstance().getProtocol(), path);
  }

  public static @NotNull String getNextAvailableName(@NotNull VirtualFile dir, @NotNull String prefix, @NotNull String extension) {
    String dotExt = PathUtil.makeFileName("", extension);
    String fileName = prefix + dotExt;
    int i = 1;
    while (dir.findChild(fileName) != null) {
      fileName = prefix + "_" + i + dotExt;
      i++;
    }
    return fileName;
  }

  /** @deprecated primitive, just inline */
  @Deprecated(forRemoval = true)
  public static @NotNull VirtualFile createChildSequent(Object requestor, @NotNull VirtualFile dir, @NotNull String prefix, @NotNull String extension) throws IOException {
    return dir.createChildData(requestor, getNextAvailableName(dir, prefix, extension));
  }

  public static String @NotNull [] filterNames(String @NotNull [] names) {
    int filteredCount = 0;
    for (String string : names) {
      if (isBadName(string)) filteredCount++;
    }
    if (filteredCount == 0) return names;

    String[] result = ArrayUtil.newStringArray(names.length - filteredCount);
    int count = 0;
    for (String string : names) {
      if (isBadName(string)) continue;
      result[count++] = string;
    }

    return result;
  }

  /**
   * Returns {@code true} if the given name is illegal from the VFS point of view.
   * Rejected are: nulls, empty strings, traversals ({@code "."} and {@code ".."}), and strings containing back- and forward slashes.
   */
  @Contract(value = "null -> true", pure = true)
  public static boolean isBadName(String name) {
    if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)) {
      return true;
    }
    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);
      if (ch == '/' || ch == '\\') {
        return true;
      }
    }
    return false;
  }

  public static VirtualFile createDirectories(@NotNull String directoryPath) throws IOException {
    return WriteAction.computeAndWait(() -> createDirectoryIfMissing(directoryPath));
  }

  public static VirtualFile createDirectoryIfMissing(@Nullable VirtualFile parent, @NotNull String relativePath) throws IOException {
    if (parent == null) {
      return createDirectoryIfMissing(LocalFileSystem.getInstance(), relativePath);
    }

    for (String each : StringUtil.split(relativePath, "/")) {
      VirtualFile child = parent.findChild(each);
      if (child == null) {
        child = parent.createChildDirectory(parent.getFileSystem(), each);
      }
      parent = child;
    }
    return parent;
  }

  public static @Nullable VirtualFile createDirectoryIfMissing(@NotNull String directoryPath) throws IOException {
    return createDirectoryIfMissing(LocalFileSystem.getInstance(), directoryPath);
  }

  public static @Nullable VirtualFile createDirectoryIfMissing(@NotNull VirtualFileSystem fileSystem,
                                                               @NotNull String directoryPath) throws IOException {
    String path = FileUtil.toSystemIndependentName(directoryPath);
    VirtualFile file = fileSystem.refreshAndFindFileByPath(path);
    if (file != null) {
      return file;
    }

    int pos = path.lastIndexOf('/');
    if (pos < 0) {
      return null;
    }

    String parentPath = StringUtil.defaultIfEmpty(path.substring(0, pos), "/");
    VirtualFile parent = createDirectoryIfMissing(fileSystem, parentPath);
    if (parent == null) {
      return null;
    }

    String dirName = path.substring(pos + 1);
    VirtualFile child = parent.findChild(dirName);
    if (child != null && child.isDirectory()) {
      return child;
    }
    return parent.createChildDirectory(fileSystem, dirName);
  }

  public static @NotNull List<VirtualFile> collectChildrenRecursively(@NotNull VirtualFile root) {
    List<VirtualFile> result = new ArrayList<>();
    visitChildrenRecursively(root, new VirtualFileVisitor<Void>(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        result.add(file);
        return true;
      }
    });
    return result;
  }

  public static void processFileRecursivelyWithoutIgnored(@NotNull VirtualFile root, @NotNull Processor<? super VirtualFile> processor) {
    FileTypeRegistry ftm = FileTypeRegistry.getInstance();
    visitChildrenRecursively(root, new VirtualFileVisitor<Void>() {
      @Override
      public @NotNull Result visitFileEx(@NotNull VirtualFile file) {
        if (!processor.process(file)) return skipTo(root);
        return file.isDirectory() && ftm.isFileIgnored(file) ? SKIP_CHILDREN : CONTINUE;
      }
    });
  }

  public static @NotNull @NlsSafe String getReadableUrl(@NotNull VirtualFile file) {
    String url = null;
    if (file.isInLocalFileSystem()) {
      url = file.getPresentableUrl();
    }
    if (url == null) {
      url = file.getUrl();
    }
    return url;
  }

  public static @Nullable VirtualFile getUserHomeDir() {
    String path = SystemProperties.getUserHome();
    return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path));
  }

  public static VirtualFile @NotNull [] getChildren(@NotNull VirtualFile dir) {
    VirtualFile[] children = dir.getChildren();
    return children == null ? VirtualFile.EMPTY_ARRAY : children;
  }

  public static @NotNull List<VirtualFile> getChildren(@NotNull VirtualFile dir, @NotNull VirtualFileFilter filter) {
    List<VirtualFile> result = null;
    for (VirtualFile child : dir.getChildren()) {
      if (filter.accept(child)) {
        if (result == null) result = new SmartList<>();
        result.add(child);
      }
    }
    return result != null ? result : List.of();
  }

  /**
   * Return a URL of the given file's parent directory.
   */
  public static @Nullable String getParentDir(@Nullable String url) {
    if (url == null) return null;
    int index = url.lastIndexOf(VfsUtilCore.VFS_SEPARATOR_CHAR);
    return index < 0 ? null : url.substring(0, index);
  }

  /**
   * Returns a name of the given file.
   */
  public static @Nullable @NlsSafe String extractFileName(@Nullable String urlOrPath) {
    if (urlOrPath == null) return null;
    int index = urlOrPath.lastIndexOf(VfsUtilCore.VFS_SEPARATOR_CHAR);
    return index < 0 ? null : urlOrPath.substring(index + 1);
  }

  public static @NotNull List<VirtualFile> markDirty(boolean recursive, boolean reloadChildren, VirtualFile @NotNull ... files) {
    var result = new ArrayList<VirtualFile>(files.length);

    for (var file : files) {
      if (file == null) continue;
      if (reloadChildren && file.isValid()) {
        file.getChildren();
      }
      if (file instanceof NewVirtualFile nvf) {
        if (recursive) {
          nvf.markDirtyRecursively();
        }
        else {
          nvf.markDirty();
        }
        result.add(file);
      }
    }

    return result;
  }

  /**
   * Refreshes the VFS information of the given files from the local file system.
   * <p/>
   * This refresh is performed without the help of the FileWatcher,
   * which means that all given files will be refreshed even if the FileWatcher didn't report any changes in them.
   * This method is slower, but more reliable, and should be preferred
   * when it is essential to make sure all the given VirtualFiles are actually refreshed from disk.
   * <p/>
   * NB: when invoking synchronous refresh from a thread other than the event dispatch thread, the current thread must
   * NOT be in a read action.
   *
   * @see VirtualFile#refresh(boolean, boolean)
   */
  public static void markDirtyAndRefresh(boolean async, boolean recursive, boolean reloadChildren, VirtualFile @NotNull ... files) {
    List<VirtualFile> list = markDirty(recursive, reloadChildren, files);
    if (list.isEmpty()) return;
    LocalFileSystem.getInstance().refreshFiles(list, async, recursive, null);
  }

  /**
   * @see #markDirtyAndRefresh(boolean, boolean, boolean, VirtualFile...)
   */
  public static void markDirtyAndRefresh(boolean async, boolean recursive, boolean reloadChildren, File @NotNull ... files) {
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    VirtualFile[] virtualFiles = ContainerUtil.map(files, fileSystem::refreshAndFindFileByIoFile, VirtualFile.EMPTY_ARRAY);
    markDirtyAndRefresh(async, recursive, reloadChildren, virtualFiles);
  }

  public static @NotNull VirtualFile getLocalFile(@NotNull VirtualFile file) {
    if (file.isValid()) {
      VirtualFileSystem fileSystem = file.getFileSystem();
      if (fileSystem instanceof ArchiveFileSystem) {
        VirtualFile localFile = ((ArchiveFileSystem)fileSystem).getLocalByEntry(file);
        if (localFile != null) {
          return localFile;
        }
      }
    }
    return file;
  }
}
