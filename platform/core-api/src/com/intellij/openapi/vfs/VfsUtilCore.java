/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.DistinctRootsCollection;
import com.intellij.util.io.URLUtil;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static com.intellij.openapi.vfs.VirtualFileVisitor.VisitorException;

public class VfsUtilCore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.VfsUtilCore");

  private static final String MAILTO = "mailto";

  public static final String LOCALHOST_URI_PATH_PREFIX = "localhost/";
  public static final char VFS_SEPARATOR_CHAR = '/';

  private static final String PROTOCOL_DELIMITER = ":";

  /**
   * Checks whether the <code>ancestor {@link VirtualFile}</code> is parent of <code>file
   * {@link VirtualFile}</code>.
   *
   * @param ancestor the file
   * @param file     the file
   * @param strict   if <code>false</code> then this method returns <code>true</code> if <code>ancestor</code>
   *                 and <code>file</code> are equal
   * @return <code>true</code> if <code>ancestor</code> is parent of <code>file</code>; <code>false</code> otherwise
   */
  public static boolean isAncestor(@NotNull VirtualFile ancestor, @NotNull VirtualFile file, boolean strict) {
    if (!file.getFileSystem().equals(ancestor.getFileSystem())) return false;
    VirtualFile parent = strict ? file.getParent() : file;
    while (true) {
      if (parent == null) return false;
      if (parent.equals(ancestor)) return true;
      parent = parent.getParent();
    }
  }

  /**
   * @return {@code true} if {@code file} is located under one of {@code roots} or equal to one of them
   */
  public static boolean isUnder(@NotNull VirtualFile file, @Nullable Set<VirtualFile> roots) {
    if (roots == null || roots.isEmpty()) return false;

    VirtualFile parent = file;
    while (parent != null) {
      if (roots.contains(parent)) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  /**
   * @return {@code true} if {@code url} is located under one of {@code rootUrls} or equal to one of them
   */
  public static boolean isUnder(@NotNull String url, @Nullable Collection<String> rootUrls) {
    if (rootUrls == null || rootUrls.isEmpty()) return false;

    for (String excludesUrl : rootUrls) {
      if (isEqualOrAncestor(excludesUrl, url)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isEqualOrAncestor(@NotNull String ancestorUrl, @NotNull String fileUrl) {
    if (ancestorUrl.equals(fileUrl)) return true;
    if (StringUtil.endsWithChar(ancestorUrl, '/')) {
      return fileUrl.startsWith(ancestorUrl);
    }
    else {
      return StringUtil.startsWithConcatenation(fileUrl, ancestorUrl, "/");
    }
  }

  public static boolean isAncestor(@NotNull File ancestor, @NotNull File file, boolean strict) {
    return FileUtil.isAncestor(ancestor, file, strict);
  }

  /**
   * Gets relative path of <code>file</code> to <code>root</code> when it's possible
   * This method is designed to be used for file descriptions (in trees, lists etc.)
   * @param file the file
   * @param root candidate to be parent file (Project base dir, any content roots etc.)
   * @return relative path of {@code file} or full path if {@code root} is not actual ancestor of {@code file}
   */
  @Nullable
  public static String getRelativeLocation(@Nullable VirtualFile file, @NotNull VirtualFile root) {
    if (file == null) return null;
    String path = getRelativePath(file, root);
    return path != null ? path : file.getPresentableUrl();
  }

  @Nullable
  public static String getRelativePath(@NotNull VirtualFile file, @NotNull VirtualFile ancestor) {
    return getRelativePath(file, ancestor, VFS_SEPARATOR_CHAR);
  }

  /**
   * Gets the relative path of <code>file</code> to its <code>ancestor</code>. Uses <code>separator</code> for
   * separating files.
   *
   * @param file      the file
   * @param ancestor  parent file
   * @param separator character to use as files separator
   * @return the relative path or {@code null} if {@code ancestor} is not ancestor for {@code file}
   */
  @Nullable
  public static String getRelativePath(@NotNull VirtualFile file, @NotNull VirtualFile ancestor, char separator) {
    if (!file.getFileSystem().equals(ancestor.getFileSystem())) {
      return null;
    }

    int length = 0;
    VirtualFile parent = file;
    while (true) {
      if (parent == null) return null;
      if (parent.equals(ancestor)) break;
      if (length > 0) {
        length++;
      }
      length += parent.getNameSequence().length();
      parent = parent.getParent();
    }

    char[] chars = new char[length];
    int index = chars.length;
    parent = file;
    while (true) {
      if (parent.equals(ancestor)) break;
      if (index < length) {
        chars[--index] = separator;
      }
      CharSequence name = parent.getNameSequence();
      for (int i = name.length() - 1; i >= 0; i--) {
        chars[--index] = name.charAt(i);
      }
      parent = parent.getParent();
    }
    return StringFactory.createShared(chars);
  }

  @Nullable
  public static VirtualFile getVirtualFileForJar(@Nullable VirtualFile entryVFile) {
    if (entryVFile == null) return null;
    final String path = entryVFile.getPath();
    final int separatorIndex = path.indexOf("!/");
    if (separatorIndex < 0) return null;

    String localPath = path.substring(0, separatorIndex);
    return VirtualFileManager.getInstance().findFileByUrl("file://" + localPath);
  }

  /**
   * Makes a copy of the <code>file</code> in the <code>toDir</code> folder and returns it.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param file      file to make a copy of
   * @param toDir     directory to make a copy in
   * @return a copy of the file
   * @throws IOException if file failed to be copied
   */
  @NotNull
  public static VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile toDir) throws IOException {
    return copyFile(requestor, file, toDir, file.getName());
  }

  /**
   * Makes a copy of the <code>file</code> in the <code>toDir</code> folder with the <code>newName</code> and returns it.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param file      file to make a copy of
   * @param toDir     directory to make a copy in
   * @param newName   new name of the file
   * @return a copy of the file
   * @throws IOException if file failed to be copied
   */
  @NotNull
  public static VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile toDir, @NotNull String newName) throws IOException {
    final VirtualFile newChild = toDir.createChildData(requestor, newName);
    newChild.setBinaryContent(file.contentsToByteArray());
    return newChild;
  }

  @NotNull
  public static InputStream byteStreamSkippingBOM(@NotNull byte[] buf, @NotNull VirtualFile file) throws IOException {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") BufferExposingByteArrayInputStream stream = new BufferExposingByteArrayInputStream(buf);
    return inputStreamSkippingBOM(stream, file);
  }

  @NotNull
  public static InputStream inputStreamSkippingBOM(@NotNull InputStream stream, @SuppressWarnings("UnusedParameters") @NotNull VirtualFile file) throws IOException {
    return CharsetToolkit.inputStreamSkippingBOM(stream);
  }

  @NotNull
  public static OutputStream outputStreamAddingBOM(@NotNull OutputStream stream, @NotNull VirtualFile file) throws IOException {
    byte[] bom = file.getBOM();
    if (bom != null) {
      stream.write(bom);
    }
    return stream;
  }

  public static boolean iterateChildrenRecursively(@NotNull final VirtualFile root,
                                                   @Nullable final VirtualFileFilter filter,
                                                   @NotNull final ContentIterator iterator) {
    final VirtualFileVisitor.Result result = visitChildrenRecursively(root, new VirtualFileVisitor() {
      @NotNull
      @Override
      public Result visitFileEx(@NotNull VirtualFile file) {
        if (filter != null && !filter.accept(file)) return SKIP_CHILDREN;
        if (!iterator.processFile(file)) return skipTo(root);
        return CONTINUE;
      }
    });
    return !Comparing.equal(result.skipToParent, root);
  }

  @SuppressWarnings({"UnsafeVfsRecursion", "Duplicates"})
  @NotNull
  public static VirtualFileVisitor.Result visitChildrenRecursively(@NotNull VirtualFile file,
                                                                   @NotNull VirtualFileVisitor<?> visitor) throws VisitorException {
    boolean pushed = false;
    try {
      final boolean visited = visitor.allowVisitFile(file);
      if (visited) {
        VirtualFileVisitor.Result result = visitor.visitFileEx(file);
        if (result.skipChildren) return result;
      }

      Iterable<VirtualFile> childrenIterable = null;
      VirtualFile[] children = null;

      try {
        if (file.isValid() && visitor.allowVisitChildren(file) && !visitor.depthLimitReached()) {
          childrenIterable = visitor.getChildrenIterable(file);
          if (childrenIterable == null) {
            children = file.getChildren();
          }
        }
      }
      catch (InvalidVirtualFileAccessException e) {
        LOG.info("Ignoring: " + e.getMessage());
        return VirtualFileVisitor.CONTINUE;
      }

      if (childrenIterable != null) {
        visitor.saveValue();
        pushed = true;
        for (VirtualFile child : childrenIterable) {
          VirtualFileVisitor.Result result = visitChildrenRecursively(child, visitor);
          if (result.skipToParent != null && !Comparing.equal(result.skipToParent, child)) return result;
        }
      }
      else if (children != null && children.length != 0) {
        visitor.saveValue();
        pushed = true;
        for (VirtualFile child : children) {
          VirtualFileVisitor.Result result = visitChildrenRecursively(child, visitor);
          if (result.skipToParent != null && !Comparing.equal(result.skipToParent, child)) return result;
        }
      }

      if (visited) {
        visitor.afterChildrenVisited(file);
      }

      return VirtualFileVisitor.CONTINUE;
    }
    finally {
      visitor.restoreValue(pushed);
    }
  }

  public static <E extends Exception> VirtualFileVisitor.Result visitChildrenRecursively(@NotNull VirtualFile file,
                                                                                         @NotNull VirtualFileVisitor visitor,
                                                                                         @NotNull Class<E> eClass) throws E {
    try {
      return visitChildrenRecursively(file, visitor);
    }
    catch (VisitorException e) {
      final Throwable cause = e.getCause();
      if (eClass.isInstance(cause)) {
        throw eClass.cast(cause);
      }
      throw e;
    }
  }

  /**
   * Returns {@code true} if given virtual file represents broken symbolic link (which points to non-existent file).
   */
  public static boolean isBrokenLink(@NotNull VirtualFile file) {
    return file.is(VFileProperty.SYMLINK) && file.getCanonicalPath() == null;
  }

  /**
   * Returns {@code true} if given virtual file represents broken or recursive symbolic link.
   */
  public static boolean isInvalidLink(@NotNull VirtualFile link) {
    final VirtualFile target = link.getCanonicalFile();
    return target == null || target.equals(link) || isAncestor(target, link, true);
  }

  @NotNull
  public static String loadText(@NotNull VirtualFile file) throws IOException {
    return loadText(file, (int)file.getLength());
  }

  @NotNull
  public static String loadText(@NotNull VirtualFile file, int length) throws IOException {
    InputStreamReader reader = new InputStreamReader(file.getInputStream(), file.getCharset());
    try {
      return StringFactory.createShared(FileUtil.loadText(reader, length));
    }
    finally {
      reader.close();
    }
  }

  @NotNull
  public static VirtualFile[] toVirtualFileArray(@NotNull Collection<? extends VirtualFile> files) {
    int size = files.size();
    if (size == 0) return VirtualFile.EMPTY_ARRAY;
    //noinspection SSBasedInspection
    return files.toArray(new VirtualFile[size]);
  }

  @NotNull
  public static String urlToPath(@Nullable String url) {
    if (url == null) return "";
    return VirtualFileManager.extractPath(url);
  }

  @NotNull
  public static File virtualToIoFile(@NotNull VirtualFile file) {
    return new File(PathUtil.toPresentableUrl(file.getUrl()));
  }

  @NotNull
  public static String pathToUrl(@NotNull String path) {
    return VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, path);
  }

  public static List<File> virtualToIoFiles(@NotNull Collection<VirtualFile> scope) {
    return ContainerUtil.map2List(scope, new Function<VirtualFile, File>() {
      @Override
      public File fun(VirtualFile file) {
        return virtualToIoFile(file);
      }
    });
  }

  @NotNull
  public static String toIdeaUrl(@NotNull String url) {
    return toIdeaUrl(url, true);
  }

  @NotNull
  public static String toIdeaUrl(@NotNull String url, boolean removeLocalhostPrefix) {
    int index = url.indexOf(":/");
    if (index < 0 || (index + 2) >= url.length()) {
      return url;
    }

    if (url.charAt(index + 2) != '/') {
      String prefix = url.substring(0, index);
      String suffix = url.substring(index + 2);

      if (SystemInfoRt.isWindows) {
        return prefix + URLUtil.SCHEME_SEPARATOR + suffix;
      }
      else if (removeLocalhostPrefix && prefix.equals(URLUtil.FILE_PROTOCOL) && suffix.startsWith(LOCALHOST_URI_PATH_PREFIX)) {
        // sometimes (e.g. in Google Chrome for Mac) local file url is prefixed with 'localhost' so we need to remove it
        return prefix + ":///" + suffix.substring(LOCALHOST_URI_PATH_PREFIX.length());
      }
      else {
        return prefix + ":///" + suffix;
      }
    }
    else if (SystemInfoRt.isWindows && (index + 3) < url.length() && url.charAt(index + 3) == '/' &&
             url.regionMatches(0, StandardFileSystems.FILE_PROTOCOL_PREFIX, 0, StandardFileSystems.FILE_PROTOCOL_PREFIX.length())) {
      // file:///C:/test/file.js -> file://C:/test/file.js
      for (int i = index + 4; i < url.length(); i++) {
        char c = url.charAt(i);
        if (c == '/') {
          break;
        }
        else if (c == ':') {
          return StandardFileSystems.FILE_PROTOCOL_PREFIX + url.substring(index + 4);
        }
      }
      return url;
    }
    return url;
  }

  @NotNull
  public static String fixURLforIDEA(@NotNull String url) {
    // removeLocalhostPrefix - false due to backward compatibility reasons
    return toIdeaUrl(url, false);
  }

  @NotNull
  public static String convertFromUrl(@NotNull URL url) {
    String protocol = url.getProtocol();
    String path = url.getPath();
    if (protocol.equals(URLUtil.JAR_PROTOCOL)) {
      if (StringUtil.startsWithConcatenation(path, URLUtil.FILE_PROTOCOL, PROTOCOL_DELIMITER)) {
        try {
          URL subURL = new URL(path);
          path = subURL.getPath();
        }
        catch (MalformedURLException e) {
          throw new RuntimeException(VfsBundle.message("url.parse.unhandled.exception"), e);
        }
      }
      else {
        throw new RuntimeException(new IOException(VfsBundle.message("url.parse.error", url.toExternalForm())));
      }
    }
    if (SystemInfo.isWindows || SystemInfo.isOS2) {
      while (!path.isEmpty() && path.charAt(0) == '/') {
        path = path.substring(1, path.length());
      }
    }

    path = URLUtil.unescapePercentSequences(path);
    return protocol + "://" + path;
  }

  /**
   * Converts VsfUrl info {@link URL}.
   *
   * @param vfsUrl VFS url (as constructed by {@link VirtualFile#getUrl()}
   * @return converted URL or null if error has occurred.
   */
  @Nullable
  public static URL convertToURL(@NotNull String vfsUrl) {
    if (vfsUrl.startsWith(StandardFileSystems.JAR_PROTOCOL_PREFIX)) {
      try {
        return new URL("jar:file:///" + vfsUrl.substring(StandardFileSystems.JAR_PROTOCOL_PREFIX.length()));
      }
      catch (MalformedURLException e) {
        return null;
      }
    }

    if (vfsUrl.startsWith(MAILTO)) {
      try {
        return new URL(vfsUrl);
      }
      catch (MalformedURLException e) {
        return null;
      }
    }

    String[] split = vfsUrl.split("://");

    if (split.length != 2) {
      LOG.debug("Malformed VFS URL: " + vfsUrl);
      return null;
    }

    String protocol = split[0];
    String path = split[1];

    try {
      if (protocol.equals(StandardFileSystems.FILE_PROTOCOL)) {
        return new URL(StandardFileSystems.FILE_PROTOCOL, "", path);
      }
      else {
        return UrlClassLoader.internProtocol(new URL(vfsUrl));
      }
    }
    catch (MalformedURLException e) {
      LOG.debug("MalformedURLException occurred:" + e.getMessage());
      return null;
    }
  }

  @NotNull
  public static String fixIDEAUrl(@NotNull String ideaUrl) {
    final String ideaProtocolMarker = "://";
    int idx = ideaUrl.indexOf(ideaProtocolMarker);
    if( idx >= 0 ) {
      String s = ideaUrl.substring(0, idx);

      if (s.equals(StandardFileSystems.JAR_PROTOCOL)) {
        s = "jar:file";
      }
      final String urlWithoutProtocol = ideaUrl.substring(idx + ideaProtocolMarker.length());
      ideaUrl = s + ":" + (urlWithoutProtocol.startsWith("/") ? "" : "/") + urlWithoutProtocol;
    }

    return ideaUrl;
  }

  @Nullable
  public static VirtualFile findRelativeFile(@NotNull String uri, @Nullable VirtualFile base) {
    if (base != null) {
      if (!base.isValid()){
        LOG.error("Invalid file name: " + base.getName() + ", url: " + uri);
      }
    }

    uri = uri.replace('\\', '/');

    if (uri.startsWith("file:///")) {
      uri = uri.substring("file:///".length());
      if (!SystemInfo.isWindows) uri = "/" + uri;
    }
    else if (uri.startsWith("file:/")) {
      uri = uri.substring("file:/".length());
      if (!SystemInfo.isWindows) uri = "/" + uri;
    }
    else uri = StringUtil.trimStart(uri, "file:");

    VirtualFile file = null;

    if (uri.startsWith("jar:file:/")) {
      uri = uri.substring("jar:file:/".length());
      if (!SystemInfo.isWindows) uri = "/" + uri;
      file = VirtualFileManager.getInstance().findFileByUrl(StandardFileSystems.JAR_PROTOCOL_PREFIX + uri);
    }
    else if (!SystemInfo.isWindows && StringUtil.startsWithChar(uri, '/') ||
             SystemInfo.isWindows && uri.length() >= 2 && Character.isLetter(uri.charAt(0)) && uri.charAt(1) == ':') {
      file = StandardFileSystems.local().findFileByPath(uri);
    }

    if (file == null && uri.contains(URLUtil.JAR_SEPARATOR)) {
      file = StandardFileSystems.jar().findFileByPath(uri);
      if (file == null && base == null) {
        file = VirtualFileManager.getInstance().findFileByUrl(uri);
      }
    }

    if (file == null) {
      if (base == null) return StandardFileSystems.local().findFileByPath(uri);
      if (!base.isDirectory()) base = base.getParent();
      if (base == null) return StandardFileSystems.local().findFileByPath(uri);
      file = VirtualFileManager.getInstance().findFileByUrl(base.getUrl() + "/" + uri);
      if (file == null) return null;
    }

    return file;
  }

  public static boolean processFilesRecursively(@NotNull VirtualFile root, @NotNull Processor<VirtualFile> processor) {
    if (!processor.process(root)) return false;

    if (root.isDirectory()) {
      final LinkedList<VirtualFile[]> queue = new LinkedList<VirtualFile[]>();

      queue.add(root.getChildren());

      do {
        final VirtualFile[] files = queue.removeFirst();

        for (VirtualFile file : files) {
          if (!processor.process(file)) return false;
          if (file.isDirectory()) {
            queue.add(file.getChildren());
          }
        }
      } while (!queue.isEmpty());
    }

    return true;
  }

  /**
   * Gets the common ancestor for passed files, or null if the files do not have common ancestors.
   *
   * @param file1 fist file
   * @param file2 second file
   * @return common ancestor for the passed files. Returns <code>null</code> if
   *         the files do not have common ancestor
   */
  @Nullable
  public static VirtualFile getCommonAncestor(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    if (!file1.getFileSystem().equals(file2.getFileSystem())) {
      return null;
    }

    VirtualFile[] path1 = getPathComponents(file1);
    VirtualFile[] path2 = getPathComponents(file2);

    int lastEqualIdx = -1;
    for (int i = 0; i < path1.length && i < path2.length; i++) {
      if (path1[i].equals(path2[i])) {
        lastEqualIdx = i;
      }
      else {
        break;
      }
    }
    return lastEqualIdx == -1 ? null : path1[lastEqualIdx];
  }

  /**
   * Gets an array of files representing paths from root to the passed file.
   *
   * @param file the file
   * @return virtual files which represents paths from root to the passed file
   */
  @NotNull
  static VirtualFile[] getPathComponents(@NotNull VirtualFile file) {
    ArrayList<VirtualFile> componentsList = new ArrayList<VirtualFile>();
    while (file != null) {
      componentsList.add(file);
      file = file.getParent();
    }
    int size = componentsList.size();
    VirtualFile[] components = new VirtualFile[size];
    for (int i = 0; i < size; i++) {
      components[i] = componentsList.get(size - i - 1);
    }
    return components;
  }

  /**
   * this collection will keep only distinct files/folders, e.g. C:\foo\bar will be removed when C:\foo is added
   */
  public static class DistinctVFilesRootsCollection extends DistinctRootsCollection<VirtualFile> {
    public DistinctVFilesRootsCollection() {
    }

    public DistinctVFilesRootsCollection(Collection<VirtualFile> virtualFiles) {
      super(virtualFiles);
    }

    public DistinctVFilesRootsCollection(VirtualFile[] collection) {
      super(collection);
    }

    @Override
    protected boolean isAncestor(@NotNull VirtualFile ancestor, @NotNull VirtualFile virtualFile) {
      return VfsUtilCore.isAncestor(ancestor, virtualFile, false);
    }
  }

  public static void processFilesRecursively(@NotNull VirtualFile root,
                                             @NotNull Processor<VirtualFile> processor,
                                             @NotNull Convertor<VirtualFile, Boolean> directoryFilter) {
    if (!processor.process(root)) return;

    if (root.isDirectory() && directoryFilter.convert(root)) {
      final LinkedList<VirtualFile[]> queue = new LinkedList<VirtualFile[]>();

      queue.add(root.getChildren());

      do {
        final VirtualFile[] files = queue.removeFirst();

        for (VirtualFile file : files) {
          if (!processor.process(file)) return;
          if (file.isDirectory() && directoryFilter.convert(file)) {
            queue.add(file.getChildren());
          }
        }
      } while (!queue.isEmpty());
    }
  }
}
