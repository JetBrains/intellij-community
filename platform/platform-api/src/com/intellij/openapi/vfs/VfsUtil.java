// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

public class VfsUtil extends VfsUtilCore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.VfsUtil");

  public static void saveText(@NotNull VirtualFile file, @NotNull String text) throws IOException {
    Charset charset = file.getCharset();
    file.setBinaryContent(text.getBytes(charset.name()));
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
   * @return a copy of the file
   * @throws IOException if file failed to be copied
   */
  public static VirtualFile copy(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile toDir) throws IOException {
    if (file.isDirectory()) {
      VirtualFile newDir = toDir.createChildDirectory(requestor, file.getName());
      copyDirectory(requestor, file, newDir, null);
      return newDir;
    }
    else {
      return copyFile(requestor, file, toDir);
    }
  }

  /**
   * Gets the array of common ancestors for passed files.
   *
   * @param files array of files
   * @return array of common ancestors for passed files
   */
  @NotNull
  public static VirtualFile[] getCommonAncestors(@NotNull VirtualFile[] files) {
    // Separate files by first component in the path.
    HashMap<VirtualFile,Set<VirtualFile>> map = new HashMap<>();
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
        filesSet = new THashSet<>();
        map.put(firstPart, filesSet);
      }
      filesSet.add(directory);
    }
    // Find common ancestor for each set of files.
    ArrayList<VirtualFile> ancestorsList = new ArrayList<>();
    for (Set<VirtualFile> filesSet : map.values()) {
      VirtualFile ancestor = null;
      for (VirtualFile file : filesSet) {
        if (ancestor == null) {
          ancestor = file;
          continue;
        }
        ancestor = getCommonAncestor(ancestor, file);
        //assertTrue(ancestor != null);
      }
      ancestorsList.add(ancestor);
      filesSet.clear();
    }
    return toVirtualFileArray(ancestorsList);
  }

  /**
   * Gets the common ancestor for passed files, or {@code null} if the files do not have common ancestors.
   */
  @Nullable
  public static VirtualFile getCommonAncestor(@NotNull Collection<? extends VirtualFile> files) {
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

  @Nullable
  public static VirtualFile findRelativeFile(@Nullable VirtualFile base, String ... path) {
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
   * Searches for the file specified by given java,net.URL.
   * Note that this method currently tested only for "file" and "jar" protocols under Unix and Windows
   *
   * @param url the URL to find file by
   * @return <code>{@link VirtualFile}</code> if the file was found, {@code null} otherwise
   */
  @Nullable
  public static VirtualFile findFileByURL(@NotNull URL url) {
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    return findFileByURL(url, virtualFileManager);
  }

  @Nullable
  public static VirtualFile findFileByURL(@NotNull URL url, @NotNull VirtualFileManager virtualFileManager) {
    String vfUrl = convertFromUrl(url);
    return virtualFileManager.findFileByUrl(vfUrl);
  }

  @Nullable
  public static VirtualFile findFileByIoFile(@NotNull File file, boolean refreshIfNeeded) {
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    VirtualFile virtualFile = fileSystem.findFileByIoFile(file);
    if (refreshIfNeeded && (virtualFile == null || !virtualFile.isValid())) {
      virtualFile = fileSystem.refreshAndFindFileByIoFile(file);
    }
    return virtualFile;
  }

  /**
   * @return correct URL, must be used only for external communication
   */
  @NotNull
  public static URI toUri(@NotNull File file) {
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
   * uri - may be incorrect (escaping or missed "/" before disk name under windows), may be not fully encoded,
   * may contains query and fragment
   * @return correct URI, must be used only for external communication
   */
  @Nullable
  public static URI toUri(@NonNls @NotNull String uri) {
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

  public static String getUrlForLibraryRoot(@NotNull File libraryRoot) {
    String path = FileUtil.toSystemIndependentName(libraryRoot.getAbsolutePath());
    if (FileTypeManager.getInstance().getFileTypeByFileName(libraryRoot.getName()) == FileTypes.ARCHIVE) {
      return VirtualFileManager.constructUrl(JarFileSystem.getInstance().getProtocol(), path + JarFileSystem.JAR_SEPARATOR);
    }
    else {
      return VirtualFileManager.constructUrl(LocalFileSystem.getInstance().getProtocol(), path);
    }
  }

  public static VirtualFile createChildSequent(Object requestor, @NotNull VirtualFile dir, @NotNull String prefix, @NotNull String extension) throws IOException {
    String dotExt = PathUtil.makeFileName("", extension);
    String fileName = prefix + dotExt;
    int i = 1;
    while (dir.findChild(fileName) != null) {
      fileName = prefix + "_" + i + dotExt;
      i++;
    }
    return dir.createChildData(requestor, fileName);
  }

  @NotNull
  public static String[] filterNames(@NotNull String[] names) {
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

  public static boolean isBadName(String name) {
    return name == null || name.isEmpty() || "/".equals(name) || "\\".equals(name);
  }

  @SuppressWarnings("RedundantThrows")
  public static VirtualFile createDirectories(@NotNull final String directoryPath) throws IOException {
    return new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        VirtualFile res = createDirectoryIfMissing(directoryPath);
        result.setResult(res);
      }
    }.execute().throwException().getResultObject();
  }

  public static VirtualFile createDirectoryIfMissing(@Nullable VirtualFile parent, String relativePath) throws IOException {
    if (parent == null) {
      return createDirectoryIfMissing(relativePath);
    }

    for (String each : StringUtil.split(relativePath, "/")) {
      VirtualFile child = parent.findChild(each);
      if (child == null) {
        child = parent.createChildDirectory(LocalFileSystem.getInstance(), each);
      }
      parent = child;
    }
    return parent;
  }

  @Nullable
  public static VirtualFile createDirectoryIfMissing(@NotNull String directoryPath) throws IOException {
    String path = FileUtil.toSystemIndependentName(directoryPath);
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    if (file == null) {
      int pos = path.lastIndexOf('/');
      if (pos < 0) return null;
      VirtualFile parent = createDirectoryIfMissing(path.substring(0, pos));
      if (parent == null) return null;
      final String dirName = path.substring(pos + 1);
      VirtualFile child = parent.findChild(dirName);
      if (child != null && child.isDirectory()) return child;
      return parent.createChildDirectory(LocalFileSystem.getInstance(), dirName);
    }
    return file;
  }

  /**
   * Returns all files in some virtual files recursively
   * @param root virtual file to get descendants
   * @return descendants
   */
  @NotNull
  public static List<VirtualFile> collectChildrenRecursively(@NotNull VirtualFile root) {
    List<VirtualFile> result = new ArrayList<>();
    visitChildrenRecursively(root, new VirtualFileVisitor(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        result.add(file);
        return true;
      }
    });
    return result;
  }

  public static void processFileRecursivelyWithoutIgnored(@NotNull VirtualFile root, @NotNull Processor<VirtualFile> processor) {
    FileTypeManager ftm = FileTypeManager.getInstance();
    visitChildrenRecursively(root, new VirtualFileVisitor() {
      @NotNull
      @Override
      public Result visitFileEx(@NotNull VirtualFile file) {
        if (!processor.process(file)) return skipTo(root);
        return file.isDirectory() && ftm.isFileIgnored(file) ? SKIP_CHILDREN : CONTINUE;
      }
    });
  }

  @NotNull
  public static String getReadableUrl(@NotNull final VirtualFile file) {
    String url = null;
    if (file.isInLocalFileSystem()) {
      url = file.getPresentableUrl();
    }
    if (url == null) {
      url = file.getUrl();
    }
    return url;
  }

  @Nullable
  public static VirtualFile getUserHomeDir() {
    final String path = SystemProperties.getUserHome();
    return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path));
  }

  @NotNull
  public static VirtualFile[] getChildren(@NotNull VirtualFile dir) {
    VirtualFile[] children = dir.getChildren();
    return children == null ? VirtualFile.EMPTY_ARRAY : children;
  }

  @NotNull
  public static List<VirtualFile> getChildren(@NotNull VirtualFile dir, @NotNull VirtualFileFilter filter) {
    List<VirtualFile> result = null;
    for (VirtualFile child : dir.getChildren()) {
      if (filter.accept(child)) {
        if (result == null) result = ContainerUtil.newSmartList();
        result.add(child);
      }
    }
    return result != null ? result : ContainerUtil.emptyList();
  }

  /**
   * @param url Url for virtual file
   * @return url for parent directory of virtual file
   */
  @Nullable
  public static String getParentDir(@Nullable final String url) {
    if (url == null) {
      return null;
    }
    final int index = url.lastIndexOf(VfsUtilCore.VFS_SEPARATOR_CHAR);
    return index < 0 ? null : url.substring(0, index);
  }

  /**
   * @param urlOrPath Url for virtual file
   * @return file name
   */
  @Nullable
  public static String extractFileName(@Nullable final String urlOrPath) {
    if (urlOrPath == null) {
      return null;
    }
    final int index = urlOrPath.lastIndexOf(VfsUtilCore.VFS_SEPARATOR_CHAR);
    return index < 0 ? null : urlOrPath.substring(index+1);
  }

  @NotNull
  public static List<VirtualFile> markDirty(boolean recursive, boolean reloadChildren, @NotNull VirtualFile... files) {
    List<VirtualFile> list = ContainerUtil.filter(files, Condition.NOT_NULL);
    if (list.isEmpty()) {
      return Collections.emptyList();
    }

    for (VirtualFile file : list) {
      if (reloadChildren && file.isValid()) {
        file.getChildren();
      }

      if (file instanceof NewVirtualFile) {
        if (recursive) {
          ((NewVirtualFile)file).markDirtyRecursively();
        }
        else {
          ((NewVirtualFile)file).markDirty();
        }
      }
    }
    return list;
  }

  /**
   * Refreshes the VFS information of the given files from the local file system.
   * <p/>
   * This refresh is performed without help of the FileWatcher,
   * which means that all given files will be refreshed even if the FileWatcher didn't report any changes in them.
   * This method is slower, but more reliable, and should be preferred
   * when it is essential to make sure all the given VirtualFiles are actually refreshed from disk.
   * <p/>
   * NB: when invoking synchronous refresh from a thread other than the event dispatch thread, the current thread must
   * NOT be in a read action.
   *
   * @see VirtualFile#refresh(boolean, boolean)
   */
  public static void markDirtyAndRefresh(boolean async, boolean recursive, boolean reloadChildren, @NotNull VirtualFile... files) {
    List<VirtualFile> list = markDirty(recursive, reloadChildren, files);
    if (list.isEmpty()) return;
    LocalFileSystem.getInstance().refreshFiles(list, async, recursive, null);
  }

  @NotNull
  public static VirtualFile getRootFile(@NotNull VirtualFile file) {
    while (true) {
      VirtualFile parent = file.getParent();
      if (parent == null) break;
      file = parent;
    }
    return file;
  }

  @NotNull
  public static VirtualFile getLocalFile(@NotNull VirtualFile file) {
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

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated to be removed in IDEA 2018 */
  public static void copyFromResource(@NotNull VirtualFile file, @NonNls @NotNull String resourceUrl) throws IOException {
    InputStream out = VfsUtil.class.getResourceAsStream(resourceUrl);
    if (out == null) {
      throw new FileNotFoundException(resourceUrl);
    }
    try {
      byte[] bytes = FileUtil.adaptiveLoadBytes(out);
      file.setBinaryContent(bytes);
    } finally {
      out.close();
    }
  }

  /** @deprecated use {@link VfsUtilCore#toIdeaUrl(String)} to be removed in IDEA 2019 */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static String toIdeaUrl(@NotNull String url) {
    return toIdeaUrl(url, true);
  }

  /** @deprecated to be removed in IDEA 2018 */
  public static <T> T processInputStream(@NotNull final VirtualFile file, @NotNull Function<InputStream, T> function) {
    InputStream stream = null;
    try {
      stream = file.getInputStream();
      return function.fun(stream);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      try {
        if (stream != null) {
          stream.close();
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  /** @deprecated to be removed in IDEA 2018 */
  public static VirtualFile copyFileRelative(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile toDir, @NotNull String relativePath) throws IOException {
    StringTokenizer tokenizer = new StringTokenizer(relativePath,"/");
    VirtualFile curDir = toDir;

    while (true) {
      String token = tokenizer.nextToken();
      if (tokenizer.hasMoreTokens()) {
        VirtualFile childDir = curDir.findChild(token);
        if (childDir == null) {
          childDir = curDir.createChildDirectory(requestor, token);
        }
        curDir = childDir;
      }
      else {
        return copyFile(requestor, file, curDir, token);
      }
    }
  }

  /** @deprecated incorrect when {@code src} is a directory; use {@link #findRelativePath(VirtualFile, VirtualFile, char)} instead */
  @Nullable
  public static String getPath(@NotNull VirtualFile src, @NotNull VirtualFile dst, char separatorChar) {
    final VirtualFile commonAncestor = getCommonAncestor(src, dst);
    if (commonAncestor != null) {
      StringBuilder buffer = new StringBuilder();
      if (!Comparing.equal(src, commonAncestor)) {
        while (!Comparing.equal(src.getParent(), commonAncestor)) {
          buffer.append("..").append(separatorChar);
          src = src.getParent();
        }
      }
      buffer.append(getRelativePath(dst, commonAncestor, separatorChar));
      return buffer.toString();
    }

    return null;
  }

  /** @deprecated incorrect, use {@link #toUri(String)} if needed (to be removed in IDEA 2019 */
  @NotNull
  public static URI toUri(@NotNull VirtualFile file) {
    String path = file.getPath();
    try {
      String protocol = file.getFileSystem().getProtocol();
      if (file.isInLocalFileSystem()) {
        if (SystemInfo.isWindows && path.charAt(0) != '/') {
          path = '/' + path;
        }
        return new URI(protocol, "", path, null, null);
      }
      if (URLUtil.HTTP_PROTOCOL.equals(protocol)) {
        return new URI(URLUtil.HTTP_PROTOCOL + URLUtil.SCHEME_SEPARATOR + path);
      }
      return new URI(protocol, path, null);
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }
  //</editor-fold>
}