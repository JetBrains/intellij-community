// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.core.CoreBundle;
import com.intellij.model.ModelBranchUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DistinctRootsCollection;
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.*;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Various utility methods for working with {@link VirtualFile}.
 */
public class VfsUtilCore {
  private static final Logger LOG = Logger.getInstance(VfsUtilCore.class);

  private static final @NonNls String MAILTO = "mailto";

  public static final @NonNls String LOCALHOST_URI_PATH_PREFIX = "localhost/";
  public static final char VFS_SEPARATOR_CHAR = '/';
  public static final String VFS_SEPARATOR = "/";

  private static final String PROTOCOL_DELIMITER = ":";

  /**
   * @param strict if {@code false} then this method returns {@code true} if {@code ancestor} and {@code file} are equal
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
  public static boolean isUnder(@NotNull VirtualFile file, @Nullable Set<? extends VirtualFile> roots) {
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
  public static boolean isUnder(@NotNull @NonNls String url, @Nullable @NonNls Collection<String> rootUrls) {
    if (rootUrls == null || rootUrls.isEmpty()) return false;

    for (String excludesUrl : rootUrls) {
      if (isEqualOrAncestor(excludesUrl, url)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isEqualOrAncestor(@NotNull @NonNls String ancestorUrl, @NotNull @NonNls String fileUrl) {
    if (ancestorUrl.equals(fileUrl)) return true;
    if (StringUtil.endsWithChar(ancestorUrl, '/')) {
      return fileUrl.startsWith(ancestorUrl);
    }
    return StringUtil.startsWithConcatenation(fileUrl, ancestorUrl, "/");
  }

  public static boolean isAncestor(@NotNull File ancestor, @NotNull File file, boolean strict) {
    return FileUtil.isAncestor(ancestor, file, strict);
  }

  /**
   * Gets a relative path of {@code file} to {@code root} when it's possible
   * This method is designed to be used for file descriptions (in trees, lists etc.)
   * @param file the file
   * @param root candidate to be parent file (Project base dir, any content roots etc.)
   * @return relative path of {@code file} or full path if {@code root} is not actual ancestor of {@code file}
   */
  public static @Nullable @NlsSafe String getRelativeLocation(@Nullable VirtualFile file, @NotNull VirtualFile root) {
    if (file == null) return null;
    String path = getRelativePath(file, root);
    return path != null ? path : file.getPresentableUrl();
  }

  public static @Nullable @NlsSafe String getRelativePath(@NotNull VirtualFile file, @NotNull VirtualFile ancestor) {
    return getRelativePath(file, ancestor, VFS_SEPARATOR_CHAR);
  }

  /**
   * Gets the relative path of {@code file} to its {@code ancestor}. Uses {@code separator} for
   * separating files.
   *
   * @param file      the file
   * @param ancestor  parent file
   * @param separator character to use as files separator
   * @return the relative path or {@code null} if {@code ancestor} is not ancestor for {@code file}
   */
  public static @Nullable @NlsSafe String getRelativePath(@NotNull VirtualFile file, @NotNull VirtualFile ancestor, char separator) {
    if (!file.getFileSystem().equals(ancestor.getFileSystem())) {
      ModelBranchUtil.checkSameBranch(file, ancestor);
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
    while (!parent.equals(ancestor)) {
      if (index < length) {
        chars[--index] = separator;
      }
      CharSequence name = parent.getNameSequence();
      for (int i = name.length() - 1; i >= 0; i--) {
        chars[--index] = name.charAt(i);
      }
      parent = parent.getParent();
    }
    return new String(chars);
  }

  /**
   * Returns the relative path from one virtual file to another.
   * If {@code src} is a file, the path is calculated from its parent directory.
   *
   * @param src           the file or directory, from which the path is built
   * @param dst           the file or directory, to which the path is built
   * @param separatorChar the separator for the path components
   * @return the relative path, or {@code null} if the files have no common ancestor
   */
  public static @Nullable String findRelativePath(@NotNull VirtualFile src, @NotNull VirtualFile dst, char separatorChar) {
    if (!src.getFileSystem().equals(dst.getFileSystem())) {
      ModelBranchUtil.checkSameBranch(src, dst);
      return null;
    }

    if (!src.isDirectory()) {
      src = src.getParent();
      if (src == null) return null;
    }

    VirtualFile commonAncestor = getCommonAncestor(src, dst);
    if (commonAncestor == null) return null;

    StringBuilder buffer = new StringBuilder();

    if (!Comparing.equal(src, commonAncestor)) {
      while (!Comparing.equal(src, commonAncestor)) {
        buffer.append("..").append(separatorChar);
        src = src.getParent();
      }
    }

    buffer.append(getRelativePath(dst, commonAncestor, separatorChar));

    if (StringUtil.endsWithChar(buffer, separatorChar)) {
      buffer.setLength(buffer.length() - 1);
    }

    return buffer.toString();
  }

  public static @Nullable VirtualFile getVirtualFileForJar(@Nullable VirtualFile entryVFile) {
    if (entryVFile == null) return null;
    String path = entryVFile.getPath();
    int separatorIndex = path.indexOf("!/");
    if (separatorIndex < 0) return null;

    String localPath = path.substring(0, separatorIndex);
    return VirtualFileManager.getInstance().findFileByUrl("file://" + localPath);
  }

  /**
   * Makes a copy of the {@code file} in the {@code toDir} folder and returns it.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if {@code requestor} is {@code null}.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param file      file to make a copy of
   * @param toDir     directory to make a copy in
   * @throws IOException if file failed to be copied
   */
  public static @NotNull VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile toDir) throws IOException {
    return copyFile(requestor, file, toDir, file.getName());
  }

  /**
   * Makes a copy of the {@code file} in the {@code toDir} folder with the {@code newName} and returns it.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if {@code requestor} is {@code null}.
   *                  See {@link VirtualFileEvent#getRequestor}
   * @param file      file to make a copy of
   * @param toDir     directory to make a copy in
   * @param newName   a new name of the file
   * @return a copy of the file
   * @throws IOException if file failed to be copied
   */
  public static @NotNull VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile toDir, @NotNull @NonNls String newName) throws IOException {
    VirtualFile newChild = toDir.createChildData(requestor, newName);
    newChild.setBinaryContent(file.contentsToByteArray(), -1, -1, requestor);
    newChild.setBOM(file.getBOM());
    return newChild;
  }

  public static @NotNull InputStream byteStreamSkippingBOM(byte @NotNull [] buf, @NotNull VirtualFile file) throws IOException {
    BufferExposingByteArrayInputStream stream = new BufferExposingByteArrayInputStream(buf);
    return inputStreamSkippingBOM(stream, file);
  }

  public static @NotNull InputStream inputStreamSkippingBOM(@NotNull InputStream stream, @SuppressWarnings("UnusedParameters") @NotNull VirtualFile file) throws IOException {
    return CharsetToolkit.inputStreamSkippingBOM(stream);
  }

  public static @NotNull OutputStream outputStreamAddingBOM(@NotNull OutputStream stream, @NotNull VirtualFile file) throws IOException {
    byte[] bom = file.getBOM();
    if (bom != null) {
      stream.write(bom);
    }
    return stream;
  }

  public static boolean iterateChildrenRecursively(@NotNull VirtualFile root,
                                                   @Nullable VirtualFileFilter filter,
                                                   @NotNull ContentIterator iterator) {
    return iterateChildrenRecursively(root, filter, iterator, new VirtualFileVisitor.Option[0]);
  }

  public static boolean iterateChildrenRecursively(@NotNull VirtualFile root,
                                                   @Nullable VirtualFileFilter filter,
                                                   @NotNull ContentIterator iterator,
                                                   VirtualFileVisitor.@NotNull Option... options) {
    VirtualFileVisitor.Result result = visitChildrenRecursively(root, new VirtualFileVisitor<Void>(options) {
      @Override
      public @NotNull Result visitFileEx(@NotNull VirtualFile file) {
        if (filter != null && !filter.accept(file)) return SKIP_CHILDREN;
        if (!iterator.processFile(file)) return skipTo(root);
        return CONTINUE;
      }
    });
    return !Comparing.equal(result.skipToParent, root);
  }

  @SuppressWarnings("UnsafeVfsRecursion")
  public static @NotNull VirtualFileVisitor.Result visitChildrenRecursively(@NotNull VirtualFile file,
                                                                            @NotNull VirtualFileVisitor<?> visitor) throws VirtualFileVisitor.VisitorException {
    ProgressManager.checkCanceled();
    boolean pushed = false;
    try {
      boolean allowVisitFile = visitor.allowVisitFile(file);
      if (allowVisitFile) {
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

      if (allowVisitFile) {
        visitor.afterChildrenVisited(file);
      }

      return VirtualFileVisitor.CONTINUE;
    }
    finally {
      visitor.restoreValue(pushed);
    }
  }

  public static <E extends Exception> VirtualFileVisitor.Result visitChildrenRecursively(@NotNull VirtualFile file,
                                                                                         @NotNull VirtualFileVisitor<?> visitor,
                                                                                         @NotNull Class<E> eClass) throws E {
    try {
      return visitChildrenRecursively(file, visitor);
    }
    catch (VirtualFileVisitor.VisitorException e) {
      Throwable cause = e.getCause();
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
    VirtualFile target = link.getCanonicalFile();
    return target == null || target.equals(link) || isAncestor(target, link, true);
  }

  public static @NotNull String loadText(@NotNull VirtualFile file) throws IOException {
    return loadText(file, (int)file.getLength());
  }

  public static @NotNull String loadText(@NotNull VirtualFile file, int length) throws IOException {
    try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), file.getCharset())) {
      return new String(FileUtilRt.loadText(reader, length));
    }
  }

  public static byte @NotNull [] loadBytes(@NotNull VirtualFile file) throws IOException {
    return FileUtilRt.isTooLarge(file.getLength()) ?
           FileUtil.loadFirstAndClose(file.getInputStream(), FileUtilRt.LARGE_FILE_PREVIEW_SIZE) :
           file.contentsToByteArray();
  }

  public static VirtualFile @NotNull [] toVirtualFileArray(@NotNull Collection<? extends VirtualFile> files) {
    return files.isEmpty() ? VirtualFile.EMPTY_ARRAY : files.toArray(VirtualFile.EMPTY_ARRAY);
  }

  public static @NotNull @NlsSafe String urlToPath(@Nullable String url) {
    return URLUtil.urlToPath(url);
  }

  /**
   * @return a {@link File} for a given {@link VirtualFile},
   * the created file may not exist or may not make sense.
   * <br />
   * It could be better and more reliably to use the {@link VirtualFile#toNioPath()}
   * <br />
   * @implNote it takes the part after ://, trims !/ at the end and turns it into a File path
   *
   * @see VirtualFile#toNioPath()
   */
  public static @NotNull File virtualToIoFile(@NotNull VirtualFile file) {
    return new File(PathUtil.toPresentableUrl(file.getUrl()));
  }

  public static @NotNull String pathToUrl(@NotNull String path) {
    return VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, FileUtil.toSystemIndependentName(path));
  }

  public static @NotNull String fileToUrl(@NotNull File file) {
    return pathToUrl(file.getPath());
  }

  /**
   * @return a {@link File} for a given {@link VirtualFile},
   * the created file may not exist or may not make sense.
   * <br />
   * It could be better and more reliably to use the {@link VirtualFile#toNioPath()}
   * <br />
   * @implNote for every item, it takes the part after ://, trims !/ at the end and turns it into a File path
   *
   * @see #virtualToIoFile(VirtualFile)
   * @see VirtualFile#toNioPath()
   */
  public static List<File> virtualToIoFiles(@NotNull Collection<? extends VirtualFile> files) {
    return ContainerUtil.map2List(files, file -> virtualToIoFile(file));
  }

  public static @NotNull String toIdeaUrl(@NotNull String url) {
    return toIdeaUrl(url, true);
  }

  public static @NotNull String toIdeaUrl(@NotNull String url, boolean removeLocalhostPrefix) {
    int index = url.indexOf(":/");
    if (index < 0 || index + 2 >= url.length()) {
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
    if (SystemInfoRt.isWindows && index + 3 < url.length() && url.charAt(index + 3) == '/' &&
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

  @SuppressWarnings("SpellCheckingInspection")
  public static @NotNull String fixURLforIDEA(@NotNull String url) {
    // removeLocalhostPrefix - false due to backward compatibility reasons
    return toIdeaUrl(url, false);
  }

  public static @NotNull String convertFromUrl(@NotNull URL url) {
    String protocol = url.getProtocol();
    String path = url.getPath();
    if (protocol.equals(URLUtil.JAR_PROTOCOL)) {
      if (StringUtil.startsWithConcatenation(path, URLUtil.FILE_PROTOCOL, PROTOCOL_DELIMITER)) {
        try {
          URL subURL = new URL(path);
          path = subURL.getPath();
        }
        catch (MalformedURLException e) {
          throw new RuntimeException(CoreBundle.message("url.parse.unhandled.exception"), e);
        }
      }
      else {
        throw new RuntimeException(new IOException(CoreBundle.message("url.parse.error", url.toExternalForm())));
      }
    }
    if (SystemInfoRt.isWindows) {
      while (!path.isEmpty() && path.charAt(0) == '/') {
        path = path.substring(1);
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
  public static @Nullable URL convertToURL(@NotNull String vfsUrl) {
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
      return URLUtil.internProtocol(new URL(vfsUrl));
    }
    catch (MalformedURLException e) {
      LOG.debug("MalformedURLException occurred:" + e.getMessage());
      return null;
    }
  }

  public static @NotNull @NlsSafe String fixIDEAUrl(@NotNull String ideaUrl) {
    String ideaProtocolMarker = "://";
    int idx = ideaUrl.indexOf(ideaProtocolMarker);
    if (idx >= 0) {
      String s = ideaUrl.substring(0, idx);
      if (s.equals(StandardFileSystems.JAR_PROTOCOL)) {
        s = "jar:file";
      }
      String urlWithoutProtocol = ideaUrl.substring(idx + ideaProtocolMarker.length());
      ideaUrl = s + ":" + (urlWithoutProtocol.startsWith("/") ? "" : "/") + urlWithoutProtocol;
    }

    return ideaUrl;
  }

  public static @Nullable VirtualFile findRelativeFile(@NotNull @NonNls String uri, @Nullable VirtualFile base) {
    if (base != null) {
      if (!base.isValid()){
        LOG.error("Invalid file name: " + base.getName() + ", url: " + uri);
      }
    }

    uri = uri.replace('\\', '/');

    if (uri.startsWith("file:///")) {
      uri = uri.substring("file:///".length());
      if (!SystemInfoRt.isWindows) {
        uri = "/" + uri;
      }
    }
    else if (uri.startsWith("file:/")) {
      uri = uri.substring("file:/".length());
      if (!SystemInfoRt.isWindows) {
        uri = "/" + uri;
      }
    }
    else {
      uri = StringUtil.trimStart(uri, "file:");
    }

    VirtualFile file = null;

    if (uri.startsWith("jar:file:/")) {
      uri = uri.substring("jar:file:/".length());
      if (!SystemInfoRt.isWindows) uri = "/" + uri;
      file = VirtualFileManager.getInstance().findFileByUrl(StandardFileSystems.JAR_PROTOCOL_PREFIX + uri);
    }
    else if (!SystemInfoRt.isWindows && StringUtil.startsWithChar(uri, '/') ||
             SystemInfoRt.isWindows && uri.length() >= 2 && Character.isLetter(uri.charAt(0)) && uri.charAt(1) == ':') {
      file = StandardFileSystems.local().findFileByPath(uri);
    }

    if (file == null && uri.contains(URLUtil.JAR_SEPARATOR)) {
      file = StandardFileSystems.jar().findFileByPath(uri);
      if (file == null && base == null) {
        file = VirtualFileManager.getInstance().findFileByUrl(uri);
      }
    }

    if (file == null) {
      if (base == null) {
        return StandardFileSystems.local().findFileByPath(uri);
      }
      if (!base.isDirectory()) {
        base = base.getParent();
      }
      if (base == null) {
        return StandardFileSystems.local().findFileByPath(uri);
      }
      file = VirtualFileManager.getInstance().findFileByUrl(base.getUrl() + "/" + uri);
    }

    return file;
  }

  public static boolean processFilesRecursively(@NotNull VirtualFile root, @NotNull Processor<? super VirtualFile> processor) {
    Ref<Boolean> result = new Ref<>(true);
    visitChildrenRecursively(root, new VirtualFileVisitor<Void>() {
      @Override
      public @NotNull Result visitFileEx(@NotNull VirtualFile file) {
        if (!processor.process(file)) {
          result.set(Boolean.FALSE);
          return skipTo(root);
        }
        return CONTINUE;
      }
    });
    return result.get();
  }

  /**
   * Returns a common ancestor for the given files, or {@code null} if the files do not have one.
   */
  public static @Nullable VirtualFile getCommonAncestor(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    if (!file1.getFileSystem().equals(file2.getFileSystem())) {
      return null;
    }

    if (file1.equals(file2)) {
      return file1;
    }

    int depth1 = depth(file1);
    int depth2 = depth(file2);

    VirtualFile parent1 = file1;
    VirtualFile parent2 = file2;
    while (depth1 > depth2 && parent1 != null) {
      parent1 = parent1.getParent();
      depth1--;
    }
    while (depth2 > depth1 && parent2 != null) {
      parent2 = parent2.getParent();
      depth2--;
    }
    while (parent1 != null && parent2 != null && !parent1.equals(parent2)) {
      parent1 = parent1.getParent();
      parent2 = parent2.getParent();
    }
    return parent1;
  }

  private static int depth(VirtualFile file) {
    int depth = 0;
    while (file != null) {
      depth++;
      file = file.getParent();
    }
    return depth;
  }

  /**
   * Gets an array of files representing paths from root to the passed file.
   *
   * @param file the file
   * @return virtual files that represents paths from root to the passed file
   */
  static VirtualFile @NotNull [] getPathComponents(@NotNull VirtualFile file) {
    List<VirtualFile> componentsList = new ArrayList<>();
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

  public static boolean hasInvalidFiles(@NotNull Iterable<? extends VirtualFile> files) {
    for (VirtualFile file : files) {
      if (!file.isValid()) {
        return true;
      }
    }
    return false;
  }

  public static @Nullable VirtualFile findContainingDirectory(@NotNull VirtualFile file, @NotNull CharSequence name) {
    VirtualFile parent = file.isDirectory() ? file: file.getParent();
    while (parent != null) {
      if (StringUtilRt.equal(parent.getNameSequence(), name, SystemInfoRt.isFileSystemCaseSensitive)) {
        return parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  /**
   * @return true if the {@code file} path is equal to the {@code path},
   * according to the file's parent directories case sensitivity.
   */
  @ApiStatus.Experimental
  public static boolean pathEqualsTo(@NotNull VirtualFile file, @NotNull @SystemIndependent String path) {
    path = FileUtil.toCanonicalPath(path);
    int li = path.length();
    while (file != null && li != -1) {
      int sepIndex = path.lastIndexOf('/', li - 1);
      CharSequence fileName = file.getNameSequence();
      int fileNameEnd = fileName.length() + (StringUtil.endsWithChar(fileName, '/') ? -1 : 0);
      if (sepIndex == 6 && StringUtil.startsWith(fileName, "//wsl$")) {
        sepIndex = -1;
      }
      if (!CharArrayUtil.regionMatches(fileName, 0, fileNameEnd, path, sepIndex + 1, li, file.isCaseSensitive())) {
        return false;
      }
      file = file.getParent();
      li = sepIndex;
    }
    return li == -1 && file == null;
  }

  private static @NotNull List<VirtualFile> getHierarchy(@NotNull VirtualFile file) {
    List<VirtualFile> result = new ArrayList<>();
    while (file != null) {
      result.add(file);
      file = file.getParent();
    }
    return result;
  }

  /**
   * @return true if the {@code ancestorPath} is equal one of {@code file}'s parents.
   * Corresponding directories case sensitivities are taken into account automatically.
   */
  @ApiStatus.Experimental
  public static boolean isAncestorOrSelf(@NotNull @SystemIndependent String ancestorPath, @NotNull VirtualFile file) {
    ancestorPath = FileUtil.toCanonicalPath(ancestorPath);
    List<VirtualFile> hierarchy = getHierarchy(file);
    if (ancestorPath.isEmpty()) {
      return true;
    }
    int i = 0;
    boolean result = false;
    int j;
    for (j = hierarchy.size() - 1; j >= 0; j--) {
      VirtualFile part = hierarchy.get(j);
      String name = part.getName();
      boolean matches = part.isCaseSensitive() ? StringUtil.startsWith(ancestorPath, i, name) :
                        StringUtilRt.startsWithIgnoreCase(ancestorPath, i, name);
      if (!matches) {
        break;
      }
      i += name.length();
      if (!name.endsWith("/")) {
        if (i != ancestorPath.length() && ancestorPath.charAt(i) != '/') {
          break;
        }
        i++;
      }
      if (i >= ancestorPath.length()) {
        result = true;
        break;
      }
    }
    return result;
  }

  /**
   * this collection will keep only distinct files/folders, e.g. C:\foo\bar will be removed when C:\foo is added
   */
  public static final class DistinctVFilesRootsCollection extends DistinctRootsCollection<VirtualFile> {
    public DistinctVFilesRootsCollection(@NotNull Collection<? extends VirtualFile> virtualFiles) {
      super(virtualFiles);
    }

    public DistinctVFilesRootsCollection(VirtualFile @NotNull [] collection) {
      super(collection);
    }

    @Override
    protected boolean isAncestor(@NotNull VirtualFile ancestor, @NotNull VirtualFile virtualFile) {
      return VfsUtilCore.isAncestor(ancestor, virtualFile, false);
    }
  }

  public static @NotNull VirtualFile getRootFile(@NotNull VirtualFile file) {
    while (true) {
      VirtualFile parent = file.getParent();
      if (parent == null) {
        break;
      }
      file = parent;
    }
    return file;
  }

  @NotNull
  public static VirtualFileSet createCompactVirtualFileSet() {
    //noinspection deprecation
    return new CompactVirtualFileSet();
  }
  @NotNull
  public static VirtualFileSet createCompactVirtualFileSet(@NotNull Collection<? extends VirtualFile> files) {
    //noinspection deprecation
    return new CompactVirtualFileSet(files);
  }
}